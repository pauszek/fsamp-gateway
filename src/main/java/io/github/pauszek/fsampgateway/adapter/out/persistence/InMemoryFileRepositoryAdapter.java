package io.github.pauszek.fsampgateway.adapter.out.persistence;

import io.github.pauszek.fsampgateway.domain.model.FileId;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;
import io.github.pauszek.fsampgateway.domain.port.out.FileRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter - In-Memory File Repository.
 * 
 * Temporary implementation for development.
 * Production should use DynamoDbFileRepositoryAdapter.
 * 
 * TODO: Implement DynamoDB adapter
 */
@Repository
public class InMemoryFileRepositoryAdapter implements FileRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryFileRepositoryAdapter.class);

    private final Map<FileId, SecureFile> storage = new ConcurrentHashMap<>();

    @Override
    public SecureFile save(SecureFile file) {
        log.debug("Saving file: fileId={}", file.getId());
        storage.put(file.getId(), file);
        return file;
    }

    @Override
    public Optional<SecureFile> findById(FileId fileId) {
        log.debug("Finding file: fileId={}", fileId);
        return Optional.ofNullable(storage.get(fileId));
    }

    @Override
    public void delete(FileId fileId) {
        log.debug("Deleting file: fileId={}", fileId);
        storage.remove(fileId);
    }

    @Override
    public boolean exists(FileId fileId) {
        return storage.containsKey(fileId);
    }
}
