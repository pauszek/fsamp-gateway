package io.github.pauszek.fsampgateway.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Value Object - Content Validation Result.
 * 
 * Represents the outcome of validating file content against declared MIME type.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidationResult {

    private final boolean valid;
    private final MimeType detectedType;
    private final String message;

    /**
     * Create a successful validation result.
     */
    public static ValidationResult valid(MimeType detectedType) {
        return new ValidationResult(true, detectedType, null);
    }

    /**
     * Create a failed validation result with reason.
     */
    public static ValidationResult invalid(MimeType detectedType, String message) {
        return new ValidationResult(false, detectedType, message);
    }

    /**
     * Check if validation passed.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Check if validation failed.
     */
    public boolean isInvalid() {
        return !valid;
    }
}
