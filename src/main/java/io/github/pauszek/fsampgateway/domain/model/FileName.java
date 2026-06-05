package io.github.pauszek.fsampgateway.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

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

    public String redactedForLogs() {
        return redactedForLogs(value);
    }

    public static String redactedForLogs(String original) {
        if (original == null || original.isBlank()) {
            return "<unknown>";
        }

        int dot = original.lastIndexOf('.');
        String extension = (dot > 0 && dot < original.length() - 1)
                ? original.substring(dot)
                : "";
        return "<redacted len=" + original.length() + " ext=" + extension + ">";
    }

    @Override
    public String toString() {
        return value;
    }
}
