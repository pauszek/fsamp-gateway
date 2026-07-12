package io.github.pauszek.fsampgateway.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record FileName(String value) {

    private static final int MAX_LENGTH = 255;
    private static final Pattern INVALID_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1F]");
    private static final Pattern PATH_TRAVERSAL = Pattern.compile("\\.\\.[\\\\/]");
    private static final Pattern UNSAFE_LOG_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");

    public FileName {
        Objects.requireNonNull(value, "File name cannot be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("File name cannot be blank");
        }

        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "File name exceeds maximum length of " + MAX_LENGTH + " characters");
        }

        if (PATH_TRAVERSAL.matcher(value).find()) {
            throw new IllegalArgumentException("File name contains path traversal pattern");
        }

        if (INVALID_CHARS.matcher(value).find()) {
            throw new IllegalArgumentException("File name contains invalid characters");
        }

        value = value.trim();
    }

    public static FileName of(String value) {
        return new FileName(value);
    }

    public String getExtension() {
        int lastDot = value.lastIndexOf('.');
        if (lastDot > 0 && lastDot < value.length() - 1) {
            return value.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    public String getBaseName() {
        int lastDot = value.lastIndexOf('.');
        if (lastDot > 0) {
            return value.substring(0, lastDot);
        }
        return value;
    }

    public String safeForLogs() {
        return safeForLogs(value);
    }

    public static String safeForLogs(String original) {
        if (original == null || original.isBlank()) {
            return "<unknown>";
        }

        return UNSAFE_LOG_CHARS.matcher(original.trim()).replaceAll("_");
    }

    @Override
    public String toString() {
        return value;
    }
}
