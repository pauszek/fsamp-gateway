package io.github.pauszek.fsampgateway.domain.port.out;

import io.github.pauszek.fsampgateway.domain.event.DomainEvent;
import io.github.pauszek.fsampgateway.domain.model.FileId;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;

import java.util.Optional;

public interface FileRepositoryPort {

    SecureFile save(SecureFile file);

    default boolean supportsTransactionalOutbox() {
        return false;
    }

    default SecureFile saveWithOutbox(SecureFile file, DomainEvent event) {
        return save(file);
    }

    Optional<SecureFile> findById(FileId fileId);

    void delete(FileId fileId);
}
