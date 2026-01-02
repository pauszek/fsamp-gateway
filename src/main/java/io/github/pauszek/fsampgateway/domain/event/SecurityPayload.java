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
     * Whether the file is encrypted (always true per schema v1.0.0).
     * JSON: "isEncrypted"
     */
    @JsonProperty("isEncrypted")
    private final boolean encrypted;
    
    /**
     * FIPS 140-3 compliant encryption algorithm.
     * Must be "AES/GCM/NoPadding" (NIST SP 800-38D AEAD).
     * AES-CBC is NOT allowed (Padding Oracle vulnerability).
     */
    private final String encryptionAlgorithm;
    
    /**
     * ARN of the AWS KMS key used for envelope encryption.
     * Required per schema v1.0.0. Must match pattern:
     * arn:aws:kms:[region]:[account]:key/[key-id]
     */
    private final String kmsKeyId;
}
