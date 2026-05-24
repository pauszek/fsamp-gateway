package io.github.pauszek.fsampgateway.domain.exception;

import io.github.pauszek.fsampgateway.domain.model.FileId;

public class FileNotFoundException extends DomainException {

    public static final String ERROR_CODE = "FILE_NOT_FOUND";

    public FileNotFoundException(FileId fileId) {
        super(ERROR_CODE, "File not found: " + fileId);
    }

    public FileNotFoundException(String message) {
        super(ERROR_CODE, message);
    }
}
