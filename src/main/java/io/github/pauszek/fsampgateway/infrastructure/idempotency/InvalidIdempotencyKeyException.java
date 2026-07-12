package io.github.pauszek.fsampgateway.infrastructure.idempotency;

public class InvalidIdempotencyKeyException extends IllegalArgumentException {

    public InvalidIdempotencyKeyException(String message) {
        super(message);
    }
}
