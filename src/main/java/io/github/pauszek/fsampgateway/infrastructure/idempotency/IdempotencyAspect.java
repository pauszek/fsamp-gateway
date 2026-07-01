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
        String idempotencyKey = getIdempotencyKeyFromRequest();
        
        if (idempotencyKey == null) {
            log.debug("No idempotency key provided, proceeding without idempotency check");
            return joinPoint.proceed();
        }

        String userId = currentUserService.getCurrentUserId().orElse("anonymous");
        
        log.info("Processing idempotent request: key={}, userId={}, method={}", 
                idempotencyKey, userId, joinPoint.getSignature().getName());

        Optional<IdempotencyKeyService.IdempotencyRecord> existingRecord = 
                idempotencyKeyService.acquireKey(idempotencyKey, userId);

        if (existingRecord.isPresent()) {
            IdempotencyKeyService.IdempotencyRecord idempotencyRecord = existingRecord.get();
            
            if (idempotencyRecord.status() == IdempotencyKeyService.KeyStatus.COMPLETED
                    && idempotencyRecord.response() != null) {
                log.info("Returning cached response for idempotency key: key={}", idempotencyKey);
                return deserializeResponse(idempotencyRecord.response(), idempotent.responseType());
            }
            
            log.warn("Idempotency key exists without cached response: key={}", idempotencyKey);
        }

        Object result;
        try {
            result = joinPoint.proceed();
            
            if (result instanceof ResponseEntity<?> responseEntity) {
                String serializedResponse = serializeResponse(responseEntity);
                idempotencyKeyService.completeKey(idempotencyKey, userId, serializedResponse);
            }
            
            return result;
            
        } catch (Exception e) {
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

    private record IdempotentResponse(int statusCode, String body) {}
}
