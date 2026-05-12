package io.github.pauszek.fsampgateway.domain.port.in;

import io.github.pauszek.fsampgateway.domain.model.FileId;

/**
 * Primary Port (Driving) - Delete File Use Case.
 * 
 * Allows administrators to soft-delete files from the platform.
 * The file metadata is updated to FAILED/deleted status and
 * optionally removed from S3 storage.
 */
public interface DeleteFileUseCase {

    /**
     * Delete a file by ID (soft-delete).
     * 
     * Marks the file as deleted in the repository and optionally
     * removes the object from S3 storage.
     *
     * @param fileId the file identifier
     * @throws io.github.pauszek.fsampgateway.domain.exception.FileNotFoundException if file not found
     */
    void execute(FileId fileId);
}
