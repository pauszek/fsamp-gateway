package io.github.pauszek.fsampgateway.domain.service;

import io.github.pauszek.fsampgateway.domain.command.UploadFileCommand;
import io.github.pauszek.fsampgateway.domain.event.FileUploadedEvent;
import io.github.pauszek.fsampgateway.domain.exception.FileValidationException;
import io.github.pauszek.fsampgateway.domain.model.*;
import io.github.pauszek.fsampgateway.domain.port.in.UploadFileUseCase;
import io.github.pauszek.fsampgateway.domain.port.out.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Domain Service - File Upload Orchestrator.
 * 
 * Coordinates the file upload workflow:
 * 1. Validate content
 * 2. Create domain entity
 * 3. Store file
 * 4. Publish event
 * 5. Persist metadata
 * 
 * This is a pure domain service - no framework dependencies.
 */
public class FileUploadDomainService implements UploadFileUseCase {

    private static final Logger log = LoggerFactory.getLogger(FileUploadDomainService.class);

    private final ContentValidatorPort contentValidator;
    private final FileStoragePort fileStorage;
    private final EventPublisherPort eventPublisher;
    private final FileRepositoryPort fileRepository;

    public FileUploadDomainService(
            ContentValidatorPort contentValidator,
            FileStoragePort fileStorage,
            EventPublisherPort eventPublisher,
            FileRepositoryPort fileRepository
    ) {
        this.contentValidator = contentValidator;
        this.fileStorage = fileStorage;
        this.eventPublisher = eventPublisher;
        this.fileRepository = fileRepository;
    }

    @Override
    public SecureFile execute(UploadFileCommand command) {
        CorrelationId correlationId = command.getCorrelationIdOrGenerate();
        MDC.put("correlationId", correlationId.value());

        try {
            log.info("Starting file upload: fileName={}, size={}, correlationId={}",
                    command.getFileName(), command.getSize(), correlationId);

            // 1. Read content for validation and checksum
            byte[] content = readContent(command);

            // 2. Validate content
            FileName fileName = FileName.of(command.getFileName());
            MimeType declaredType = MimeType.of(command.getContentType());
            
            ValidationResult validationResult = contentValidator.validate(
                    new ByteArrayInputStream(content),
                    declaredType,
                    command.getFileName()
            );

            if (validationResult.isInvalid()) {
                throw new FileValidationException(validationResult.getMessage());
            }

            MimeType validatedType = validationResult.getDetectedType();
            if (!validatedType.isAllowed()) {
                throw new FileValidationException(
                        "File type '" + validatedType + "' is not allowed");
            }

            // 3. Compute checksum
            Checksum checksum = contentValidator.computeChecksum(content);

            // 4. Create domain entity
            SecureFile file = SecureFile.createPending(
                    fileName,
                    validatedType,
                    FileSize.of(command.getSize()),
                    correlationId,
                    command.getUploadedBy()
            );

            log.debug("Created pending file entity: fileId={}", file.getId());

            // 5. Store file
            var storageResult = fileStorage.store(
                    file.getId(),
                    new ByteArrayInputStream(content),
                    file.getSize(),
                    file.getMimeType(),
                    StorageMetadata.of(
                            correlationId.value(),
                            fileName.value(),
                            checksum.value()
                    )
            );

            log.debug("File stored: location={}", storageResult.getLocation());

            // 6. Update entity with storage info
            file = file.markAsUploaded(
                    storageResult.getLocation(),
                    storageResult.getEncryptionMetadata(),
                    checksum
            );

            // 7. Persist metadata
            file = fileRepository.save(file);
            log.debug("File metadata persisted: fileId={}", file.getId());

            // 8. Publish domain event
            FileUploadedEvent event = FileUploadedEvent.from(file);
            String messageId = eventPublisher.publish(event);
            log.info("Published FILE_UPLOADED event: messageId={}, fileId={}", 
                    messageId, file.getId());

            log.info("File upload completed: fileId={}, status={}", 
                    file.getId(), file.getStatus());

            return file;

        } finally {
            MDC.remove("correlationId");
        }
    }

    private byte[] readContent(UploadFileCommand command) {
        try {
            return command.getContent().readAllBytes();
        } catch (IOException e) {
            throw new FileValidationException("Failed to read file content", e);
        }
    }
}
