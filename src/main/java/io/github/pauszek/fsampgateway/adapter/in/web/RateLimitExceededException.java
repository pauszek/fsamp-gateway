package io.github.pauszek.fsampgateway.adapter.in.web;

/**
 * Exception thrown when rate limit is exceeded.
 * Results in HTTP 429 Too Many Requests response.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
