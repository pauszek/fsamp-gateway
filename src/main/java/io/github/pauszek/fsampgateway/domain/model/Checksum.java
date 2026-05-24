package io.github.pauszek.fsampgateway.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record Checksum(String value, Algorithm algorithm) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[a-f0-9]{64}$");

    public enum Algorithm {
        SHA256("SHA-256");

        private final String displayName;

        Algorithm(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public Checksum {
        Objects.requireNonNull(value, "Checksum value cannot be null");
        Objects.requireNonNull(algorithm, "Checksum algorithm cannot be null");
        
        value = value.toLowerCase();
        
        if (algorithm == Algorithm.SHA256 && !SHA256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid SHA-256 checksum format: expected 64 hex characters");
        }
    }

    public static Checksum sha256(String value) {
        return new Checksum(value, Algorithm.SHA256);
    }

    public boolean matches(Checksum other) {
        if (other == null) return false;
        return this.algorithm == other.algorithm && 
               this.value.equalsIgnoreCase(other.value);
    }

    @Override
    public String toString() {
        return algorithm.getDisplayName() + ":" + value;
    }
}
