package io.github.pauszek.fsampgateway.domain.exception;

public class EventSerializationException extends DomainException {

    public static final String ERROR_CODE = "EVENT_SERIALIZATION_ERROR";

    public EventSerializationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
