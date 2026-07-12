package io.github.pauszek.fsampgateway.domain.service;

import io.github.pauszek.fsampgateway.domain.exception.FileNotFoundException;
import io.github.pauszek.fsampgateway.domain.model.FileId;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;
import io.github.pauszek.fsampgateway.domain.port.in.DeleteFileUseCase;
import io.github.pauszek.fsampgateway.domain.port.in.GetFileUseCase;
import io.github.pauszek.fsampgateway.domain.port.out.FileRepositoryPort;
import io.github.pauszek.fsampgateway.domain.port.out.FileStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class FileQueryDomainService implements GetFileUseCase, DeleteFileUseCase {

    private static final Logger log = LoggerFactory.getLogger(FileQueryDomainService.class);

    private final FileRepositoryPort fileRepository;
    private final FileStoragePort fileStorage;

    public FileQueryDomainService(
            FileRepositoryPort fileRepository,
            FileStoragePort fileStorage) {
        this.fileRepository = fileRepository;
        this.fileStorage = fileStorage;
    }

    @Override
    public Optional<SecureFile> getById(FileId fileId) {
        log.debug("Getting file by ID: fileId={}", fileId);
        return fileRepository.findById(fileId);
    }

    @Override
    public SecureFile getByIdOrThrow(FileId fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));
    }

    @Override
    public void execute(FileId fileId) {
        log.info("Deleting file: fileId={}", fileId);

        SecureFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        SecureFile deletingFile = file.markAsDeleting();
        fileRepository.save(deletingFile);

        if (file.getStorageLocation() != null) {
            fileStorage.delete(file.getStorageLocation());
            log.info("File removed from storage: fileId={}, location={}",
                    fileId, file.getStorageLocation());
        }

        fileRepository.delete(fileId);
        log.info("File deleted: fileId={}", fileId);
    }
}
