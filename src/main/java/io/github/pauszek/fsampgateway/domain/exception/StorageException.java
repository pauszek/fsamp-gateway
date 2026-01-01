package io.github.pauszek.fsampgateway.domain.exception;

/**
 * Exception thrown when storage operations fail.
 */
public class StorageException extends DomainException {

    public static final String ERROR_CODE = "STORAGE_ERROR";

    public StorageException(String message) {
        super(ERROR_CODE, message);
    }

    public StorageException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
