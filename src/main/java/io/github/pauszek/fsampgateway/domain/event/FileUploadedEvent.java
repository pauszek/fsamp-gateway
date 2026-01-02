package io.github.pauszek.fsampgateway.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.pauszek.fsampgateway.domain.model.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event - File Uploaded.
 * 
 * Published when a file is successfully uploaded to storage.
 * Follows the event.schema.json contract (JSON Schema Draft-07).
 * Compliant with FIPS 140-3 cryptographic requirements.
 */
public record FileUploadedEvent(
        String schemaVersion,
        UUID eventId,
        UUID correlationId,
        Instant timestamp,
        String source,
        String eventType,
        FilePayload fileMetadata,
        StoragePayload storageLocation,
        SecurityPayload securityContext
) implements DomainEvent {

    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String EVENT_TYPE = "FILE_UPLOADED";
    public static final String SOURCE = "fsamp-gateway";

    public FileUploadedEvent {
        if (schemaVersion == null) schemaVersion = SCHEMA_VERSION;
        if (eventId == null) eventId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
        if (source == null) source = SOURCE;
        if (eventType == null) eventType = EVENT_TYPE;
    }

    /**
     * Create from SecureFile domain entity.
     */
    public static FileUploadedEvent from(SecureFile file) {
        return new FileUploadedEvent(
                SCHEMA_VERSION,
                UUID.randomUUID(),
                correlationIdToUUID(file.getCorrelationId().value()),
                Instant.now(),
                SOURCE,
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
    
    /**
     * Convert 32-character hex correlation ID to UUID format.
     * Correlation ID format: 32 hex chars (e.g., "a1b2c3d4e5f67890a1b2c3d4e5f67890")
     * UUID format: 8-4-4-4-12 with dashes (e.g., "a1b2c3d4-e5f6-7890-a1b2-c3d4e5f67890")
     */
    private static UUID correlationIdToUUID(String correlationId) {
        if (correlationId.length() != 32) {
            throw new IllegalArgumentException("Correlation ID must be 32 characters, got: " + correlationId.length());
        }
        String uuid = correlationId.substring(0, 8) + "-" +
                correlationId.substring(8, 12) + "-" +
                correlationId.substring(12, 16) + "-" +
                correlationId.substring(16, 20) + "-" +
                correlationId.substring(20, 32);
        return UUID.fromString(uuid);
    }

    @Override
    public String getEventType() {
        return eventType;
    }

    /**
     * Helper method for DomainEvent interface.
     * Not serialized to JSON - use 'timestamp' field instead.
     */
    @JsonIgnore
    @Override
    public Instant getOccurredAt() {
        return timestamp;
    }
}
