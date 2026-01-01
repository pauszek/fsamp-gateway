package io.github.pauszek.fsampgateway.domain.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Payload containing storage location for domain events.
 * 
 * Immutable value object that captures where the file is stored.
 */
@Getter
@RequiredArgsConstructor(staticName = "of")
public final class StoragePayload {
    
    private final String bucketName;
    private final String objectKey;
}
