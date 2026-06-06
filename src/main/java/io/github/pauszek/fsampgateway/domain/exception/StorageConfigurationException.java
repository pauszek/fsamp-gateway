package io.github.pauszek.fsampgateway.domain.exception;

public class StorageConfigurationException extends DomainException {

    public static final String ERROR_CODE = "STORAGE_CONFIGURATION_ERROR";

    public StorageConfigurationException(String message) {
        super(ERROR_CODE, message);
    }
}
