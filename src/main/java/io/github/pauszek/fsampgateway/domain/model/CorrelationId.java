package io.github.pauszek.fsampgateway.domain.model;

import java.util.Objects;
import java.util.UUID;

public record CorrelationId(String value) {

    public CorrelationId {
        Objects.requireNonNull(value, "Correlation ID value cannot be null");
        String candidate = value.trim().toLowerCase();
        if (candidate.matches("^[a-f0-9]{32}$")) {
            candidate = candidate.substring(0, 8) + "-"
                    + candidate.substring(8, 12) + "-"
                    + candidate.substring(12, 16) + "-"
                    + candidate.substring(16, 20) + "-"
                    + candidate.substring(20);
        }
        UUID uuid = parseUuid(candidate, value);
        if (uuid.version() != 4 || uuid.variant() != 2) {
            throw new IllegalArgumentException("Correlation ID must be a UUID v4: " + value);
        }
        value = uuid.toString();
    }

    public static CorrelationId generate() {
        return new CorrelationId(UUID.randomUUID().toString());
    }

    public static CorrelationId of(String value) {
        if (value == null || value.isBlank()) {
            return generate();
        }
        return new CorrelationId(value);
    }

    private static UUID parseUuid(String candidate, String original) {
        try {
            return UUID.fromString(candidate);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Correlation ID must be a UUID v4: " + original, e);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
