package io.github.pauszek.fsampgateway.domain.model;

import java.time.Instant;
import java.util.Objects;

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

    public static AuditInfo create(String createdBy) {
        Instant now = Instant.now();
        return new AuditInfo(createdBy, now, now);
    }

    public AuditInfo update() {
        return new AuditInfo(this.createdBy, this.createdAt, Instant.now());
    }

}
