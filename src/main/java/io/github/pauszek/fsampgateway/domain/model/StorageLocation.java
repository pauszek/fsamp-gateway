package io.github.pauszek.fsampgateway.domain.model;

import java.util.Objects;

/**
 * Value Object - Storage Location.
 * 
 * Represents where a file is stored (S3 bucket + key).
 * Implements the Claim-Check pattern - events reference this instead of file content.
 */
public record StorageLocation(String bucketName, String objectKey) {

    public StorageLocation {
        Objects.requireNonNull(bucketName, "Bucket name cannot be null");
        Objects.requireNonNull(objectKey, "Object key cannot be null");
        
        if (bucketName.isBlank()) {
            throw new IllegalArgumentException("Bucket name cannot be blank");
        }
        if (objectKey.isBlank()) {
            throw new IllegalArgumentException("Object key cannot be blank");
        }
    }

    /**
     * Create StorageLocation.
     */
    public static StorageLocation of(String bucketName, String objectKey) {
        return new StorageLocation(bucketName, objectKey);
    }

    /**
     * Get S3 URI format.
     */
    public String toS3Uri() {
        return "s3://" + bucketName + "/" + objectKey;
    }

    /**
     * Get ARN format.
     */
    public String toArn() {
        return "arn:aws:s3:::" + bucketName + "/" + objectKey;
    }

    @Override
    public String toString() {
        return toS3Uri();
    }
}
