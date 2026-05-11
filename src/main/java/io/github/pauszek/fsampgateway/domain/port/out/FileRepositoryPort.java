package io.github.pauszek.fsampgateway.domain.port.out;

import io.github.pauszek.fsampgateway.domain.event.DomainEvent;
import io.github.pauszek.fsampgateway.domain.model.FileId;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;

import java.util.Optional;

/**
 * Secondary Port (Driven) - File Repository.
 * 
 * This is the interface for persisting file metadata.
 * Implementation is in the adapter layer (e.g., DynamoDB adapter).
 */
public interface FileRepositoryPort {

    /**
     * Save file metadata.
     *
     * @param file the file to save
     * @return the saved file
     */
    SecureFile save(SecureFile file);

    /**
     * Whether this repository can atomically persist metadata with an outbox event.
     */
    default boolean supportsTransactionalOutbox() {
        return false;
    }

    /**
     * Save file metadata and an outbox event in one transaction.
     *
     * <p>Implementations that do not support the transactional outbox should keep the
     * default behavior, allowing the application service to fall back to direct publish.
     *
     * @param file the file to save
     * @param event the event to enqueue
     * @return the saved file
     */
    default SecureFile saveWithOutbox(SecureFile file, DomainEvent event) {
        return save(file);
    }

    /**
     * Find file by ID.
     *
     * @param fileId the file identifier
     * @return the file if found
     */
    Optional<SecureFile> findById(FileId fileId);

    /**
     * Delete file metadata.
     *
     * @param fileId the file identifier
     */
    void delete(FileId fileId);

    /**
     * Check if file exists.
     *
     * @param fileId the file identifier
     * @return true if exists
     */
    boolean exists(FileId fileId);
}
