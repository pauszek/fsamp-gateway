package io.github.pauszek.fsampgateway.domain.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Payload containing security context for domain events.
 * 
 * Immutable value object that captures encryption and security
 * information about the file.
 */
@Getter
@RequiredArgsConstructor(staticName = "of")
public final class SecurityPayload {
    
    private final boolean encrypted;
    private final String encryptionAlgorithm;
    private final String kmsKeyId;
}
