package io.github.pauszek.fsampgateway.domain.port.in;

import io.github.pauszek.fsampgateway.domain.model.FileId;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;

import java.util.Optional;

/**
 * Primary Port (Driving) - Get File Use Case.
 * 
 * Query operations for file retrieval.
 */
public interface GetFileUseCase {

    /**
     * Get file by ID.
     *
     * @param fileId the file identifier
     * @return the file if found
     */
    Optional<SecureFile> getById(FileId fileId);

    /**
     * Get file by ID (throws if not found).
     *
     * @param fileId the file identifier
     * @return the file
     * @throws io.github.pauszek.fsampgateway.domain.exception.FileNotFoundException if not found
     */
    SecureFile getByIdOrThrow(FileId fileId);
}
