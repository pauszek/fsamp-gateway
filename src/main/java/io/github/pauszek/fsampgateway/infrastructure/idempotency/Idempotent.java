package io.github.pauszek.fsampgateway.infrastructure.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as idempotent.
 * 
 * When a method is annotated with @Idempotent, the IdempotencyAspect will:
 * 1. Check for X-Idempotency-Key header in the request
 * 2. If key exists and was already processed: return cached response
 * 3. If key is new: process request, cache response, return result
 * 4. If processing fails: clean up key so request can be retried
 * 
 * Usage:
 * <pre>
 * {@code
 * @PostMapping("/upload")
 * @Idempotent(responseType = FileUploadResponseDto.class)
 * public ResponseEntity<FileUploadResponseDto> uploadFile(...) {
 *     // Implementation
 * }
 * }
 * </pre>
 * 
 * Client usage:
 * <pre>
 * curl -X POST /api/v1/files/upload \
 *   -H "Authorization: Bearer <token>" \
 *   -H "X-Idempotency-Key: unique-request-id-123" \
 *   -F "file=@document.pdf"
 * </pre>
 * 
 * Best practices for clients:
 * - Use UUID v4 for idempotency keys
 * - Store the key client-side before making the request
 * - Reuse the same key when retrying a failed request
 * - Generate a new key for each unique business operation
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    
    /**
     * The response type class for deserialization of cached responses.
     * Required for proper type handling when returning cached results.
     */
    Class<?> responseType();
}
