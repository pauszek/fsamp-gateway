package io.github.pauszek.fsampgateway.infrastructure.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pauszek.fsampgateway.infrastructure.security.cognito.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Idempotency Aspect.
 * 
 * AOP aspect that automatically handles idempotency for annotated methods.
 * 
 * Features:
 * - Extracts X-Idempotency-Key header from request
 * - Checks for duplicate requests
 * - Returns cached response for duplicates
 * - Stores response after successful processing
 * - Cleans up on failure
 * 
 * Usage:
 * Annotate controller methods with @Idempotent
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotencyAspect {

    public static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    
    private final IdempotencyKeyService idempotencyKeyService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // Get the idempotency key from request header
        String idempotencyKey = getIdempotencyKeyFromRequest();
        
        if (idempotencyKey == null) {
            // No idempotency key provided, proceed normally
            log.debug("No idempotency key provided, proceeding without idempotency check");
            return joinPoint.proceed();
        }

        String userId = currentUserService.getCurrentUserId().orElse("anonymous");
        
        log.info("Processing idempotent request: key={}, userId={}, method={}", 
                idempotencyKey, userId, joinPoint.getSignature().getName());

        // Try to acquire the key
        Optional<IdempotencyKeyService.IdempotencyRecord> existingRecord = 
                idempotencyKeyService.acquireKey(idempotencyKey, userId);

        if (existingRecord.isPresent()) {
            IdempotencyKeyService.IdempotencyRecord record = existingRecord.get();
            
            if (record.status() == IdempotencyKeyService.KeyStatus.COMPLETED && record.response() != null) {
                // Return cached response
                log.info("Returning cached response for idempotency key: key={}", idempotencyKey);
                return deserializeResponse(record.response(), idempotent.responseType());
            }
            
            // Key exists but no response cached (shouldn't happen normally)
            log.warn("Idempotency key exists without cached response: key={}", idempotencyKey);
        }

        // Process the request
        Object result;
        try {
            result = joinPoint.proceed();
            
            // Store the successful response
            if (result instanceof ResponseEntity<?> responseEntity) {
                String serializedResponse = serializeResponse(responseEntity);
                idempotencyKeyService.completeKey(idempotencyKey, userId, serializedResponse);
            }
            
            return result;
            
        } catch (Exception e) {
            // Clean up on failure so request can be retried
            idempotencyKeyService.failKey(idempotencyKey, userId);
            throw e;
        }
    }

    private String getIdempotencyKeyFromRequest() {
        ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes == null) {
            return null;
        }
        
        HttpServletRequest request = attributes.getRequest();
        return request.getHeader(IDEMPOTENCY_KEY_HEADER);
    }

    private String serializeResponse(ResponseEntity<?> responseEntity) {
        try {
            IdempotentResponse cached = new IdempotentResponse(
                    responseEntity.getStatusCode().value(),
                    objectMapper.writeValueAsString(responseEntity.getBody())
            );
            return objectMapper.writeValueAsString(cached);
        } catch (Exception e) {
            log.error("Failed to serialize response for idempotency cache", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> deserializeResponse(String serialized, Class<T> responseType) {
        try {
            IdempotentResponse cached = objectMapper.readValue(serialized, IdempotentResponse.class);
            T body = objectMapper.readValue(cached.body(), responseType);
            return ResponseEntity.status(cached.statusCode()).body(body);
        } catch (Exception e) {
            log.error("Failed to deserialize cached response", e);
            throw new RuntimeException("Failed to deserialize cached response", e);
        }
    }

    /**
     * Internal record for serializing cached responses.
     */
    private record IdempotentResponse(int statusCode, String body) {}
}
