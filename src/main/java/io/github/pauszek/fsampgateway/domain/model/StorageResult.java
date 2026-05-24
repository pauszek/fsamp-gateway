package io.github.pauszek.fsampgateway.domain.model;

import io.github.pauszek.fsampgateway.domain.model.EncryptionMetadata;
import io.github.pauszek.fsampgateway.domain.model.StorageLocation;
import lombok.Getter;

@Getter
public final class StorageResult {
    
    private final StorageLocation location;
    private final EncryptionMetadata encryptionMetadata;
    private final String etag;

    private StorageResult(StorageLocation location, EncryptionMetadata encryptionMetadata, String etag) {
        this.location = location;
        this.encryptionMetadata = encryptionMetadata;
        this.etag = etag;
    }

    public static StorageResult of(StorageLocation location, EncryptionMetadata encryptionMetadata, String etag) {
        return new StorageResult(location, encryptionMetadata, etag);
    }
}
