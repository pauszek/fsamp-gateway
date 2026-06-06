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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class FileUploadDomainService implements UploadFileUseCase {

    private static final Logger log = LoggerFactory.getLogger(FileUploadDomainService.class);
    private static final String UPLOAD_TEMP_DIR_NAME = "fsamp-gateway-uploads";
    private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY_PERMISSIONS =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> OWNER_ONLY_FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY_DIRECTORY =
            PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY_PERMISSIONS);
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY_FILE =
            PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE_PERMISSIONS);

    private final ContentValidatorPort contentValidator;
    private final FileStoragePort fileStorage;
    private final EventPublisherPort eventPublisher;
    private final FileRepositoryPort fileRepository;
    private final boolean directPublishAfterOutbox;

    public FileUploadDomainService(
            ContentValidatorPort contentValidator,
            FileStoragePort fileStorage,
            EventPublisherPort eventPublisher,
            FileRepositoryPort fileRepository
    ) {
        this(contentValidator, fileStorage, eventPublisher, fileRepository, false);
    }

    public FileUploadDomainService(
            ContentValidatorPort contentValidator,
            FileStoragePort fileStorage,
            EventPublisherPort eventPublisher,
            FileRepositoryPort fileRepository,
            boolean directPublishAfterOutbox
    ) {
        this.contentValidator = contentValidator;
        this.fileStorage = fileStorage;
        this.eventPublisher = eventPublisher;
        this.fileRepository = fileRepository;
        this.directPublishAfterOutbox = directPublishAfterOutbox;
    }

    @Override
    public SecureFile execute(UploadFileCommand command) {
        CorrelationId correlationId = command.getCorrelationIdOrGenerate();
        MDC.put("correlationId", correlationId.value());

        Path tempFile = null;
        try {
            if (log.isInfoEnabled()) {
                log.info("Starting file upload: fileName={}, size={}, correlationId={}",
                        FileName.safeForLogs(command.getFileName()), command.getSize(), correlationId);
            }
            tempFile = bufferToTempFile(command);
            FileName fileName = FileName.of(command.getFileName());
            MimeType declaredType = MimeType.of(command.getContentType());

            ValidationResult validationResult;
            try (InputStream is = Files.newInputStream(tempFile)) {
                validationResult = contentValidator.validate(is, declaredType, command.getFileName());
            }

            if (validationResult.isInvalid()) {
                throw new FileValidationException(validationResult.getMessage());
            }

            MimeType validatedType = validationResult.getDetectedType();
            if (!validatedType.isAllowed()) {
                throw new FileValidationException(
                        "File type '" + validatedType + "' is not allowed");
            }
            Checksum checksum;
            try (InputStream is = Files.newInputStream(tempFile)) {
                checksum = contentValidator.computeChecksum(is);
            }
            SecureFile file = SecureFile.createPending(
                    fileName,
                    validatedType,
                    FileSize.of(command.getSize()),
                    correlationId,
                    command.getUploadedBy()
            );

            log.debug("Created pending file entity: fileId={}", file.getId());
            StorageResult storageResult;
            try (InputStream is = Files.newInputStream(tempFile)) {
                storageResult = fileStorage.store(
                        file.getId(),
                        is,
                        file.getSize(),
                        file.getMimeType(),
                        StorageMetadata.of(
                                correlationId.value(),
                                fileName.value(),
                                checksum.value()
                        )
                );
            }

            log.debug("File stored: location={}", storageResult.getLocation());
            file = file.markAsUploaded(
                    storageResult.getLocation(),
                    storageResult.getEncryptionMetadata(),
                    checksum
            );
            FileUploadedEvent event = FileUploadedEvent.from(file);
            if (fileRepository.supportsTransactionalOutbox()) {
                file = fileRepository.saveWithOutbox(file, event);
                log.info("File metadata and FILE_UPLOADED outbox event persisted: fileId={}, eventId={}",
                        file.getId(), event.eventId());
                if (directPublishAfterOutbox) {
                    String messageId = eventPublisher.publish(event);
                    log.info("Published FILE_UPLOADED event directly after outbox write (local fallback): messageId={}, fileId={}",
                            messageId, file.getId());
                }
            } else {
                file = fileRepository.save(file);
                log.debug("File metadata persisted: fileId={}", file.getId());

                String messageId = eventPublisher.publish(event);
                log.info("Published FILE_UPLOADED event directly: messageId={}, fileId={}",
                        messageId, file.getId());
            }

            log.info("File upload completed: fileId={}, status={}",
                    file.getId(), file.getStatus());

            return file;

        } catch (IOException e) {
            throw new FileValidationException("I/O error during file upload", e);
        } finally {
            deleteTempFile(tempFile);
            MDC.remove("correlationId");
        }
    }

    private Path bufferToTempFile(UploadFileCommand command) {
        try {
            Path temp = createSecureTempFile();
            try (InputStream in = command.getContent();
                 OutputStream out = Files.newOutputStream(temp)) {
                in.transferTo(out);
            }
            return temp;
        } catch (IOException e) {
            throw new FileValidationException("Failed to buffer file content", e);
        }
    }

    @SuppressWarnings("java:S5443")
    private Path createSecureTempFile() throws IOException {
        Path uploadTempDirectory = secureUploadTempDirectory();
        try {
            return Files.createTempFile(uploadTempDirectory, "fsamp-upload-", ".tmp", OWNER_ONLY_FILE);
        } catch (UnsupportedOperationException e) {
            return Files.createTempFile(uploadTempDirectory, "fsamp-upload-", ".tmp");
        }
    }

    @SuppressWarnings("java:S5443")
    private Path secureUploadTempDirectory() throws IOException {
        Path baseTempDirectory = Path.of(System.getProperty("java.io.tmpdir"))
                .toAbsolutePath()
                .normalize();
        Path uploadTempDirectory = baseTempDirectory.resolve(UPLOAD_TEMP_DIR_NAME);

        if (Files.isSymbolicLink(uploadTempDirectory)
                || (Files.exists(uploadTempDirectory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(uploadTempDirectory, LinkOption.NOFOLLOW_LINKS))) {
            throw new FileValidationException("Unsafe upload temp directory");
        }

        try {
            Path directory = Files.createDirectories(uploadTempDirectory, OWNER_ONLY_DIRECTORY);
            Files.setPosixFilePermissions(directory, OWNER_ONLY_DIRECTORY_PERMISSIONS);
            return directory;
        } catch (UnsupportedOperationException e) {
            return Files.createDirectories(uploadTempDirectory);
        }
    }

    private void deleteTempFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("Failed to delete temp file: {}", path, e);
            }
        }
    }
}
