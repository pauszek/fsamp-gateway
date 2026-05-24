package io.github.pauszek.fsampgateway.domain.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(staticName = "of")
public final class FilePayload {
    
    private final String originalFilename;
    
    private final long fileSizeBytes;
    
    private final String mimeType;
    
    private final String checksumSHA256;
}
