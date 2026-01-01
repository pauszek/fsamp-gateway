package io.github.pauszek.fsampgateway.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object - File Name.
 * 
 * Validates and sanitizes file names to prevent security issues.
 */
public record FileName(String value) {

    private static final int MAX_LENGTH = 255;
    private static final Pattern INVALID_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1F]");
    private static final Pattern PATH_TRAVERSAL = Pattern.compile("\\.\\.[\\\\/]");

    public FileName {
        Objects.requireNonNull(value, "File name cannot be null");
        
        if (value.isBlank()) {
            throw new IllegalArgumentException("File name cannot be blank");
        }
        
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "File name exceeds maximum length of " + MAX_LENGTH + " characters");
        }
        
        // Security: Check for path traversal
        if (PATH_TRAVERSAL.matcher(value).find()) {
            throw new IllegalArgumentException("File name contains path traversal pattern");
        }
        
        // Security: Check for invalid characters
        if (INVALID_CHARS.matcher(value).find()) {
            throw new IllegalArgumentException("File name contains invalid characters");
        }
        
        // Trim whitespace
        value = value.trim();
    }

    /**
     * Create FileName from raw input.
     */
    public static FileName of(String value) {
        return new FileName(value);
    }

    /**
     * Get file extension (without dot).
     */
    public String getExtension() {
        int lastDot = value.lastIndexOf('.');
        if (lastDot > 0 && lastDot < value.length() - 1) {
            return value.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Get base name (without extension).
     */
    public String getBaseName() {
        int lastDot = value.lastIndexOf('.');
        if (lastDot > 0) {
            return value.substring(0, lastDot);
        }
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
