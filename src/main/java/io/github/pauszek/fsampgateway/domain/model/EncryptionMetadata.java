package io.github.pauszek.fsampgateway.domain.model;

import java.util.Objects;

public record EncryptionMetadata(
        String kmsKeyId,
        EncryptionAlgorithm algorithm,
        boolean encrypted
) {

    public enum EncryptionAlgorithm {
        AES_256_GCM("AES/GCM/NoPadding", "AES-256-GCM");


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

    public static EncryptionMetadata kmsEncrypted(String kmsKeyId) {
        return new EncryptionMetadata(kmsKeyId, EncryptionAlgorithm.AES_256_GCM, true);
    }

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
