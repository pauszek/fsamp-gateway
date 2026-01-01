package io.github.pauszek.fsampgateway.domain.port.in;

import io.github.pauszek.fsampgateway.domain.command.UploadFileCommand;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;

/**
 * Primary Port (Driving) - Upload File Use Case.
 * 
 * This is the interface that adapters (REST controller, CLI, etc.)
 * use to trigger the file upload workflow.
 * 
 * Following CQRS pattern - this is a Command use case.
 */
public interface UploadFileUseCase {

    /**
     * Upload a file to the system.
     *
     * @param command upload command with all required data
     * @return the created SecureFile entity
     */
    SecureFile execute(UploadFileCommand command);
}
