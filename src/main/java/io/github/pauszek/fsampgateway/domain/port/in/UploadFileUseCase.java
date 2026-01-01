package io.github.pauszek.fsampgateway.domain.port.in;

import io.github.pauszek.fsampgateway.domain.model.CorrelationId;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;

import java.io.InputStream;

/**
 * Primary Port (Driving) - Upload File Use Case.
 * 
 * This is the interface that adapters (REST controller, CLI, etc.)
 * use to trigger the file upload workflow.
 */
public interface UploadFileUseCase {

    /**
     * Upload a file to the system.
     *
     * @param command upload command with all required data
     * @return the created SecureFile entity
     */
    SecureFile execute(UploadFileCommand command);

    /**
     * Command object for file upload.
     * Contains all data needed to execute the use case.
     */
    record UploadFileCommand(
            String fileName,
            String contentType,
            long size,
            InputStream content,
            String correlationId,
            String uploadedBy
    ) {
        public CorrelationId getCorrelationIdOrGenerate() {
            return CorrelationId.of(correlationId);
        }
    }
}
