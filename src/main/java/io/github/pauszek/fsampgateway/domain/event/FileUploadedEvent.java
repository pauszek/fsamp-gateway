package io.github.pauszek.fsampgateway.domain.event;

import io.github.pauszek.fsampgateway.domain.model.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event - File Uploaded.
 * 
 * Published when a file is successfully uploaded to storage.
 * Follows the CloudEvents specification format.
 */
public record FileUploadedEvent(
        UUID eventId,
        String correlationId,
        Instant timestamp,
        String eventType,
        FilePayload fileMetadata,
        StoragePayload storageLocation,
        SecurityPayload securityContext
) implements DomainEvent {

    public static final String EVENT_TYPE = "FILE_UPLOADED";

    public FileUploadedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
        if (eventType == null) eventType = EVENT_TYPE;
    }

    /**
     * Create from SecureFile domain entity.
     */
    public static FileUploadedEvent from(SecureFile file) {
        return new FileUploadedEvent(
                UUID.randomUUID(),
                file.getCorrelationId().value(),
                Instant.now(),
                EVENT_TYPE,
                new FilePayload(
                        file.getFileName().value(),
                        file.getSize().bytes(),
                        file.getMimeType().value()
                ),
                new StoragePayload(
                        file.getStorageLocation().bucketName(),
                        file.getStorageLocation().objectKey()
                ),
                new SecurityPayload(
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

    @Override
    public Instant getOccurredAt() {
        return timestamp;
    }

    public record FilePayload(
            String originalFilename,
            long fileSizeBytes,
            String mimeType
    ) {}

    public record StoragePayload(
            String bucketName,
            String objectKey
    ) {}

    public record SecurityPayload(
            boolean isEncrypted,
            String encryptionAlgorithm,
            String kmsKeyId
    ) {}
}
