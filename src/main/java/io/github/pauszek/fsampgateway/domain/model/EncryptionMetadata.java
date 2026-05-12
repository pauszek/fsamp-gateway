package io.github.pauszek.fsampgateway.domain.model;

import java.util.Objects;

/**
 * Value Object - Encryption Metadata.
 * 
 * Contains cryptographic information for the FIPS 140-3-oriented security posture.
 * Only AES-256-GCM is permitted per NIST SP 800-38D.
 */
public record EncryptionMetadata(
        String kmsKeyId,
        EncryptionAlgorithm algorithm,
        boolean encrypted
) {

    /**
     * FIPS 140-3-oriented encryption algorithms.
     * Only AES-GCM is permitted - provides authenticated encryption (AEAD).
     */
    public enum EncryptionAlgorithm {
        /**
         * AES-256 in Galois/Counter Mode - NIST SP 800-38D approved.
         * Provides both confidentiality and authenticity (AEAD).
         */
        AES_256_GCM("AES/GCM/NoPadding", "AES-256-GCM");
        
        // NOTE: AES-CBC removed - vulnerable to Padding Oracle attacks
        // and requires separate MAC for integrity (not AEAD)

        private final String javaName;
        private final String displayName;

        EncryptionAlgorithm(String javaName, String displayName) {
            this.javaName = javaName;
            this.displayName = displayName;
        }

        public String getJavaName() {
            return javaName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public EncryptionMetadata {
        Objects.requireNonNull(kmsKeyId, "KMS Key ID cannot be null");
        Objects.requireNonNull(algorithm, "Encryption algorithm cannot be null");
    }

    /**
     * Create encryption metadata for server-side encryption with KMS.
     */
    public static EncryptionMetadata kmsEncrypted(String kmsKeyId) {
        return new EncryptionMetadata(kmsKeyId, EncryptionAlgorithm.AES_256_GCM, true);
    }

    /**
     * Create encryption metadata with specific algorithm.
     */
    public static EncryptionMetadata of(String kmsKeyId, EncryptionAlgorithm algorithm) {
        return new EncryptionMetadata(kmsKeyId, algorithm, true);
    }

    public String getAlgorithmName() {
        return algorithm.getJavaName();
    }

    @Override
    public String toString() {
        return "EncryptionMetadata{" +
                "kmsKeyId='" + maskKeyId() + '\'' +
                ", algorithm=" + algorithm.getDisplayName() +
                ", encrypted=" + encrypted +
                '}';
    }

    private String maskKeyId() {
        if (kmsKeyId == null || kmsKeyId.length() <= 8) {
            return "***";
        }
        return kmsKeyId.substring(0, 4) + "..." + kmsKeyId.substring(kmsKeyId.length() - 4);
    }
}
