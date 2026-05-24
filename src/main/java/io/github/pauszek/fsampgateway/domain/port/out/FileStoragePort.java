package io.github.pauszek.fsampgateway.domain.port.out;

import io.github.pauszek.fsampgateway.domain.model.*;

import java.io.InputStream;

public interface FileStoragePort {

    StorageResult store(
            FileId fileId,
            InputStream content,
            FileSize size,
            MimeType mimeType,
            StorageMetadata metadata
    );

    InputStream retrieve(StorageLocation location);

    void delete(StorageLocation location);

    boolean exists(StorageLocation location);
}
