package io.github.pauszek.fsampgateway.domain.port.out;

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
