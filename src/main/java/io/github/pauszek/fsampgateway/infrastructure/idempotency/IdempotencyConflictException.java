package io.github.pauszek.fsampgateway.infrastructure.idempotency;

/**
 * Exception thrown when an idempotency key conflict is detected.
 * 
 * This occurs when:
 * - A request with the same idempotency key is already being processed
 * - Two concurrent requests try to use the same key
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }

    public IdempotencyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
