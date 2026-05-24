package io.github.pauszek.fsampgateway.domain.model;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record CorrelationId(String value) {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-f0-9]{32}$");

    public CorrelationId {
        Objects.requireNonNull(value, "Correlation ID value cannot be null");
        if (!VALID_PATTERN.matcher(value.toLowerCase()).matches()) {
            throw new IllegalArgumentException(
                    "Correlation ID must be a 32-character hex string, got: " + value);
        }
        value = value.toLowerCase();
    }

    public static CorrelationId generate() {
        return new CorrelationId(UUID.randomUUID().toString().replace("-", ""));
    }

    public static CorrelationId of(String value) {
        if (value == null || value.isBlank()) {
            return generate();
        }
        return new CorrelationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
