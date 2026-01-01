package io.github.pauszek.fsampgateway.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Metadata associated with file storage operation.
 * 
 * Contains information about the original file that should be
 * preserved alongside the stored content.
 */
@Getter
@RequiredArgsConstructor(staticName = "of")
public final class StorageMetadata {
    
    private final String correlationId;
    private final String originalFilename;
    private final String checksum;
}
