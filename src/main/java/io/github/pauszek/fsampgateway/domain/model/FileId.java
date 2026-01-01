package io.github.pauszek.fsampgateway.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object - File Identifier.
 * 
 * Represents a unique file identifier using UUID.
 * Immutable and validated on creation.
 */
public record FileId(UUID value) {

    public FileId {
        Objects.requireNonNull(value, "File ID value cannot be null");
    }

    /**
     * Generate a new random FileId.
     */
    public static FileId generate() {
        return new FileId(UUID.randomUUID());
    }

    /**
     * Create FileId from string representation.
     */
    public static FileId of(String value) {
        Objects.requireNonNull(value, "File ID string cannot be null");
        try {
            return new FileId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid File ID format: " + value, e);
        }
    }

    /**
     * Create FileId from UUID.
     */
    public static FileId of(UUID value) {
        return new FileId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
