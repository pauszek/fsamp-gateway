package io.github.pauszek.fsampgateway.domain.port.out;

import io.github.pauszek.fsampgateway.domain.model.*;

import java.io.InputStream;

/**
 * Secondary Port (Driven) - File Storage.
 * 
 * This is the interface that the domain uses to store files.
 * Implementation is in the adapter layer (e.g., S3Adapter).
 */
public interface FileStoragePort {

    /**
     * Store a file and return the storage location.
     *
     * @param fileId      unique file identifier
     * @param content     file content stream
     * @param size        file size in bytes
     * @param mimeType    content type
     * @param metadata    additional metadata
     * @return storage location (bucket + key)
     */
    StorageResult store(
            FileId fileId,
            InputStream content,
            FileSize size,
            MimeType mimeType,
            StorageMetadata metadata
    );

    /**
     * Download a file.
     *
     * @param location storage location
     * @return file content stream
     */
    InputStream retrieve(StorageLocation location);

    /**
     * Delete a file.
     *
     * @param location storage location
     */
    void delete(StorageLocation location);

    /**
     * Check if file exists.
     *
     * @param location storage location
     * @return true if exists
     */
    boolean exists(StorageLocation location);
}
