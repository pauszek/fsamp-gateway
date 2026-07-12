package io.github.pauszek.fsampgateway.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.pauszek.fsampgateway.domain.model.*;

import java.time.Instant;
import java.util.UUID;

public record FileUploadedEvent(
        String schemaVersion,
        UUID fileId,
        UUID eventId,
        UUID correlationId,
        Instant timestamp,
        String source,
        String eventType,
        FilePayload fileMetadata,
        StoragePayload storageLocation,
        SecurityPayload securityContext
) implements DomainEvent {

    public static final String SCHEMA_VERSION = "1.2.0";
    public static final String EVENT_TYPE = "FILE_UPLOADED";
    public static final String EVENT_SOURCE = "fsamp-gateway";

    public FileUploadedEvent {
        if (schemaVersion == null) schemaVersion = SCHEMA_VERSION;
        if (fileId == null) fileId = UUID.randomUUID();
        if (eventId == null) eventId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
        if (source == null) source = EVENT_SOURCE;
        if (eventType == null) eventType = EVENT_TYPE;
    }

    public static FileUploadedEvent from(SecureFile file) {
        return new FileUploadedEvent(
                SCHEMA_VERSION,
                file.getId().value(),
                UUID.randomUUID(),
                UUID.fromString(file.getCorrelationId().value()),
                Instant.now(),
                EVENT_SOURCE,
                EVENT_TYPE,
                FilePayload.of(
                        file.getFileName().value(),
                        file.getSize().bytes(),
                        file.getMimeType().value(),
                        file.getChecksum().value()
                ),
                StoragePayload.of(
                        file.getStorageLocation().bucketName(),
                        file.getStorageLocation().objectKey()
                ),
                SecurityPayload.of(
                        true,
                        file.getEncryptionMetadata().getAlgorithmName(),
                        file.getEncryptionMetadata().kmsKeyId()
                )
        );
    }

    @Override
    public String getEventType() {
        return eventType;
    }

    @JsonIgnore
    @Override
    public Instant getOccurredAt() {
        return timestamp;
    }
}
