package io.github.pauszek.fsampgateway.domain.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Payload containing file metadata for domain events.
 * 
 * Immutable value object that captures essential file information
 * to be included in event messages.
 */
@Getter
@RequiredArgsConstructor(staticName = "of")
public final class FilePayload {
    
    private final String originalFilename;
    private final long fileSizeBytes;
    private final String mimeType;
}
