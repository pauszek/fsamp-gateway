package io.github.pauszek.fsampgateway.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Payload containing security context for domain events.
 * 
 * Immutable value object that captures encryption and security
 * information about the file.
 * 
 * JSON field naming follows the event.schema.json contract.
 */
@Getter
@RequiredArgsConstructor(staticName = "of")
public final class SecurityPayload {
    
    /**
     * Whether the file is encrypted (required by schema).
     * JSON: "isEncrypted"
     */
    @JsonProperty("isEncrypted")
    private final boolean encrypted;
    
    /**
     * FIPS-compliant encryption algorithm.
     * Must be one of: "AES/GCM/NoPadding", "AES/CBC/PKCS5Padding"
     */
    private final String encryptionAlgorithm;
    
    /**
     * ARN of the AWS KMS key used for envelope encryption.
     */
    private final String kmsKeyId;
}
