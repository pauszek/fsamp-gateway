package io.github.pauszek.fsampgateway.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Value Object - Audit Information.
 * 
 * Tracks who/when for compliance and debugging.
 */
public record AuditInfo(
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    public AuditInfo {
        Objects.requireNonNull(createdBy, "Created by cannot be null");
        Objects.requireNonNull(createdAt, "Created at cannot be null");
        Objects.requireNonNull(updatedAt, "Updated at cannot be null");
    }

    /**
     * Create new audit info for a new entity.
     */
    public static AuditInfo create(String createdBy) {
        Instant now = Instant.now();
        return new AuditInfo(createdBy, now, now);
    }

    /**
     * Create updated audit info (preserves created info).
     */
    public AuditInfo update() {
        return new AuditInfo(this.createdBy, this.createdAt, Instant.now());
    }

    /**
     * Create for system operations.
     */
    public static AuditInfo system() {
        return create("SYSTEM");
    }

    /**
     * Create for anonymous uploads (local dev).
     */
    public static AuditInfo anonymous() {
        return create("ANONYMOUS");
    }
}
