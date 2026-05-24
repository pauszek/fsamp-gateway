package io.github.pauszek.fsampgateway.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(staticName = "of")
public final class StorageMetadata {
    
    private final String correlationId;
    private final String originalFilename;
    private final String checksum;
}
