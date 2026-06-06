package io.github.pauszek.fsampgateway.adapter.out.storage;

import io.github.pauszek.fsampgateway.domain.exception.StorageConfigurationException;
import io.github.pauszek.fsampgateway.domain.exception.StorageException;
import io.github.pauszek.fsampgateway.domain.model.*;
import io.github.pauszek.fsampgateway.domain.port.out.FileStoragePort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import org.springframework.beans.factory.annotation.Value;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Component
public class S3StorageAdapter implements FileStoragePort {

    private static final Logger log = LoggerFactory.getLogger(S3StorageAdapter.class);
    private static final String CIRCUIT_BREAKER_NAME = "s3Storage";

    private final S3Client s3Client;
    private final S3StorageProperties properties;
    
    @Value("${KMS_KEY_ID:#{null}}")
    private String fallbackKmsKeyId;

    public S3StorageAdapter(S3Client s3Client, S3StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
        log.info("S3StorageAdapter initialized: bucketName={}, kmsKeyId={}", 
                properties.getBucketName(), properties.getKmsKeyId());
    }
    
    private String getKmsKeyId() {
        String keyId = properties.getKmsKeyId();
        if (keyId == null || keyId.isBlank()) {
            keyId = fallbackKmsKeyId;
            log.debug("Using fallback KMS Key ID: {}", keyId);
        }
        if (keyId == null || keyId.isBlank()) {
            throw new StorageConfigurationException("KMS key id is required for S3 SSE-KMS encryption");
        }
        return keyId;
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "storeFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public StorageResult store(
            FileId fileId,
            InputStream content,
            FileSize size,
            MimeType mimeType,
            StorageMetadata metadata
    ) {
        String objectKey = generateObjectKey(fileId);
        String bucketName = properties.getBucketName();
        String kmsKeyId = getKmsKeyId();

        log.info("Storing file: fileId={}, bucket={}, key={}", fileId, bucketName, objectKey);

        try {
            Map<String, String> s3Metadata = new HashMap<>();
            s3Metadata.put("correlation-id", metadata.getCorrelationId());
            s3Metadata.put("original-filename", sanitizeMetadataValue(
                    FileName.redactedForLogs(metadata.getOriginalFilename())));
            if (metadata.getChecksum() != null) {
                s3Metadata.put("checksum-sha256", metadata.getChecksum());
            }

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(mimeType.value())
                    .contentLength(size.bytes())
                    .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                    .ssekmsKeyId(kmsKeyId)
                    .metadata(s3Metadata)
                    .build();

            PutObjectResponse response = s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(content, size.bytes())
            );

            log.info("File stored successfully: fileId={}, etag={}", fileId, response.eTag());

            return StorageResult.of(
                    StorageLocation.of(bucketName, objectKey),
                    EncryptionMetadata.kmsEncrypted(kmsKeyId),
                    response.eTag()
            );

        } catch (S3Exception e) {
            log.error("Failed to store file: fileId={}, error={}", fileId, e.getMessage(), e);
            throw new StorageException("Failed to store file in S3: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private StorageResult storeFallback(
            FileId fileId,
            InputStream content,
            FileSize size,
            MimeType mimeType,
            StorageMetadata metadata,
            Exception e
    ) {
        log.error("Circuit breaker fallback for store: fileId={}, error={}", fileId, e.getMessage());
        throw new StorageException("Storage service unavailable", e);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public InputStream retrieve(StorageLocation location) {
        log.debug("Retrieving file: {}", location);

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(location.bucketName())
                    .key(location.objectKey())
                    .build();

            return s3Client.getObject(request);

        } catch (NoSuchKeyException e) {
            log.warn("File not found: {}", location);
            throw new io.github.pauszek.fsampgateway.domain.exception.FileNotFoundException(
                    "File not found at " + location);
        } catch (S3Exception e) {
            log.error("Failed to retrieve file: {}, error={}", location, e.getMessage(), e);
            throw new StorageException("Failed to retrieve file from S3", e);
        }
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public void delete(StorageLocation location) {
        log.info("Deleting file: {}", location);

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(location.bucketName())
                    .key(location.objectKey())
                    .build();

            s3Client.deleteObject(request);
            log.info("File deleted: {}", location);

        } catch (S3Exception e) {
            log.error("Failed to delete file: {}, error={}", location, e.getMessage(), e);
            throw new StorageException("Failed to delete file from S3", e);
        }
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public boolean exists(StorageLocation location) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(location.bucketName())
                    .key(location.objectKey())
                    .build();

            s3Client.headObject(request);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.error("Error checking file existence: {}, error={}", location, e.getMessage(), e);
            throw new StorageException("Failed to check file existence", e);
        }
    }

    private String generateObjectKey(FileId fileId) {
        // Always derive the key from UTC so the partition is identical
        // regardless of the JVM default timezone. Mismatched zones would
        // otherwise cause the gateway and the processor to disagree about
        // which prefix the object lives under.
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return String.format("uploads/%d/%02d/%02d/%s",
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                fileId.value().toString()
        );
    }

    private String sanitizeMetadataValue(String value) {
        if (value == null) return "";
        return value.replaceAll("[^\\x20-\\x7E]", "_");
    }
}
