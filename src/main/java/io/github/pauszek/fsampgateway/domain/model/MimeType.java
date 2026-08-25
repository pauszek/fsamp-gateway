package io.github.pauszek.fsampgateway.domain.model;

import java.util.Locale;
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
        value = value.toLowerCase(Locale.ROOT).trim();
    }

    public static MimeType of(String value) {
        return new MimeType(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
