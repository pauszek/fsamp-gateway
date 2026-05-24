package io.github.pauszek.fsampgateway.domain.model;

import java.util.Objects;
import java.util.Set;

public record MimeType(String value) {

    public static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/gif",
            "application/json",
            "text/plain",
            "text/csv",
            "application/xml",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    public MimeType {
        Objects.requireNonNull(value, "MIME type cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("MIME type cannot be blank");
        }
        value = value.toLowerCase().trim();
    }

    public static MimeType of(String value) {
        return new MimeType(value);
    }

    public boolean isAllowed() {
        return ALLOWED_TYPES.contains(value);
    }

    public boolean isImage() {
        return value.startsWith("image/");
    }

    public boolean isDocument() {
        return value.equals("application/pdf") ||
               value.startsWith("application/vnd.openxmlformats");
    }

    @Override
    public String toString() {
        return value;
    }
}
