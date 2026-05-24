package io.github.pauszek.fsampgateway.domain.exception;

public class EventPublishException extends DomainException {

    public static final String ERROR_CODE = "EVENT_PUBLISH_ERROR";

    public EventPublishException(String message) {
        super(ERROR_CODE, message);
    }

    public EventPublishException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
