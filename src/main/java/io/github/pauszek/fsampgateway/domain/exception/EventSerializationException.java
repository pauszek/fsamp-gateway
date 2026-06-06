package io.github.pauszek.fsampgateway.domain.exception;

public class EventSerializationException extends EventPublishException {

    public EventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
