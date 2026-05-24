package io.github.pauszek.fsampgateway.domain.exception;

public class FileValidationException extends DomainException {

    public static final String ERROR_CODE = "FILE_VALIDATION_ERROR";

    public FileValidationException(String message) {
        super(ERROR_CODE, message);
    }

    public FileValidationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
