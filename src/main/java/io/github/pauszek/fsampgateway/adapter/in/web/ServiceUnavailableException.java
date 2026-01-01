package io.github.pauszek.fsampgateway.adapter.in.web;

/**
 * Exception thrown when service is temporarily unavailable.
 * Results in HTTP 503 Service Unavailable response.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
