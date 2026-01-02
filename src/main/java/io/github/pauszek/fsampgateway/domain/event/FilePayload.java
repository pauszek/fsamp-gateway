package io.github.pauszek.fsampgateway.domain.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Payload containing file metadata for domain events.
 * 
 * Immutable value object that captures essential file information
 * to be included in event messages.
 * 
 * Follows event.schema.json contract.
 */
@Getter
@RequiredArgsConstructor(staticName = "of")
public final class FilePayload {
    
    /**
     * Original name of the uploaded file.
     */
    private final String originalFilename;
    
    /**
     * Size of the file in bytes (max 100MB = 104857600).
     */
    private final long fileSizeBytes;
    
    /**
     * MIME type of the file content.
     */
    private final String mimeType;
    
    /**
     * SHA-256 hash of file content for integrity verification (FIPS 180-4).
     * 64 hex characters lowercase.
     */
    private final String checksumSHA256;
}
