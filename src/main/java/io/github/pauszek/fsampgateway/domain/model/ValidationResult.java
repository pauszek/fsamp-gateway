package io.github.pauszek.fsampgateway.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidationResult {

    private final boolean valid;
    private final MimeType detectedType;
    private final String message;

    public static ValidationResult valid(MimeType detectedType) {
        return new ValidationResult(true, detectedType, null);
    }

    public static ValidationResult invalid(MimeType detectedType, String message) {
        return new ValidationResult(false, detectedType, message);
    }

    public boolean isInvalid() {
        return !valid;
    }
}
