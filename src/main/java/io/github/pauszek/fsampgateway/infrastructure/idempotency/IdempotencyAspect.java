package io.github.pauszek.fsampgateway.infrastructure.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pauszek.fsampgateway.application.dto.FileUploadRequestDto;
import io.github.pauszek.fsampgateway.domain.exception.FileValidationException;
import io.github.pauszek.fsampgateway.infrastructure.security.cognito.CurrentUserService;
import io.github.pauszek.fsampgateway.infrastructure.security.Sha256Digest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@Slf4j
@RequiredArgsConstructor
public class IdempotencyAspect {

    public static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

    private final IdempotencyKeyService idempotencyKeyService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = currentRequest();
        String idempotencyKey = request == null ? null : request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return joinPoint.proceed();
        }
        idempotencyKey = idempotencyKey.trim();

        String userId = currentUserService.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required"));
        String fingerprint = requestFingerprint(request, joinPoint.getArgs());
        IdempotencyKeyService.Acquisition acquisition = idempotencyKeyService.acquireKey(
                idempotencyKey,
                userId,
                fingerprint
        );

        if (acquisition.hasCachedResponse()) {
            return deserializeResponse(acquisition.cachedRecord().response(), idempotent.responseType());
        }

        try {
            Object result = joinPoint.proceed();
            if (result instanceof ResponseEntity<?> responseEntity) {
                cacheSuccessfulResponse(idempotencyKey, userId, acquisition.ownerToken(), responseEntity);
            }
            return result;
        } catch (Throwable failure) {
            releaseKeyAfterFailure(idempotencyKey, userId, acquisition.ownerToken(), failure);
            throw failure;
        }
    }

    private void cacheSuccessfulResponse(
            String idempotencyKey,
            String userId,
            String ownerToken,
            ResponseEntity<?> responseEntity
    ) {
        try {
            idempotencyKeyService.completeKey(
                    idempotencyKey,
                    userId,
                    ownerToken,
                    serializeResponse(responseEntity)
            );
        } catch (RuntimeException cacheFailure) {
            log.error("Request succeeded but its idempotent response could not be persisted", cacheFailure);
        }
    }

    private void releaseKeyAfterFailure(
            String idempotencyKey,
            String userId,
            String ownerToken,
            Throwable failure
    ) {
        try {
            idempotencyKeyService.failKey(idempotencyKey, userId, ownerToken);
        } catch (RuntimeException releaseFailure) {
            failure.addSuppressed(releaseFailure);
        }
    }

    private String requestFingerprint(HttpServletRequest request, Object[] arguments) {
        try {
            Sha256Digest digest = Sha256Digest.create();
            update(digest, request == null ? "" : request.getMethod());
            update(digest, request == null ? "" : request.getRequestURI());

            for (Object argument : arguments) {
                if (argument instanceof MultipartFile file) {
                    update(digest, file.getOriginalFilename());
                    update(digest, file.getContentType());
                    update(digest, Long.toString(file.getSize()));
                    try (InputStream content = file.getInputStream()) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = content.read(buffer)) != -1) {
                            digest.update(buffer, 0, read);
                        }
                    }
                } else if (argument instanceof FileUploadRequestDto metadata) {
                    update(digest, objectMapper.writeValueAsString(metadata));
                }
            }
            return HexFormat.of().formatHex(digest.finish());
        } catch (Exception e) {
            throw new FileValidationException("Cannot fingerprint upload request", e);
        }
    }

    private String serializeResponse(ResponseEntity<?> responseEntity) {
        try {
            CachedHttpResponse response = new CachedHttpResponse(
                    responseEntity.getStatusCode().value(),
                    responseEntity.getHeaders(),
                    responseEntity.getBody() == null
                            ? null
                            : objectMapper.writeValueAsString(responseEntity.getBody())
            );
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
    }

    private <T> ResponseEntity<T> deserializeResponse(String serialized, Class<T> responseType) {
        try {
            CachedHttpResponse cached = objectMapper.readValue(serialized, CachedHttpResponse.class);
            T body = cached.body() == null ? null : objectMapper.readValue(cached.body(), responseType);
            HttpHeaders headers = new HttpHeaders();
            cached.headers().forEach(headers::put);
            return ResponseEntity.status(cached.statusCode()).headers(headers).body(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize idempotent response", e);
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static void update(Sha256Digest digest, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private record CachedHttpResponse(
            int statusCode,
            Map<String, List<String>> headers,
            String body
    ) {}
}
