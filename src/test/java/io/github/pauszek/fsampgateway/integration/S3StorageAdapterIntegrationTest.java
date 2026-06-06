package io.github.pauszek.fsampgateway.integration;

import io.github.pauszek.fsampgateway.adapter.out.storage.S3StorageAdapter;
import io.github.pauszek.fsampgateway.adapter.out.storage.S3StorageProperties;
import io.github.pauszek.fsampgateway.domain.model.FileId;
import io.github.pauszek.fsampgateway.domain.model.FileName;
import io.github.pauszek.fsampgateway.domain.model.FileSize;
import io.github.pauszek.fsampgateway.domain.model.MimeType;
import io.github.pauszek.fsampgateway.domain.model.StorageMetadata;
import io.github.pauszek.fsampgateway.domain.model.StorageResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("S3StorageAdapter Integration Tests")
class S3StorageAdapterIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private S3StorageProperties s3Properties;

    private S3StorageAdapter s3StorageAdapter;

    @BeforeEach
    void setUp() {
        s3Properties.setBucketName(TEST_BUCKET);
        s3StorageAdapter = new S3StorageAdapter(s3Client, s3Properties);
    }

    @Nested
    @DisplayName("store")
    class Store {

        @Test
        @DisplayName("should store file in S3 bucket")
        void shouldStoreFileInS3Bucket() {
            String content = "Test file content for S3 integration test";
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            FileId fileId = FileId.generate();
            InputStream inputStream = new ByteArrayInputStream(contentBytes);
            FileSize fileSize = FileSize.of(contentBytes.length);
            MimeType mimeType = MimeType.of("text/plain");
            StorageMetadata metadata = StorageMetadata.of(
                    UUID.randomUUID().toString().replace("-", ""),
                    "test-file.txt",
                    "abc123def456"
            );

            StorageResult result = s3StorageAdapter.store(fileId, inputStream, fileSize, mimeType, metadata);

            assertThat(result).isNotNull();
            assertThat(result.getLocation()).isNotNull();
            assertThat(result.getLocation().bucketName()).isEqualTo(TEST_BUCKET);
            assertThat(result.getLocation().objectKey()).contains(fileId.value().toString());
        }

        @Test
        @DisplayName("should store and retrieve file content")
        void shouldStoreAndRetrieveFileContent() throws IOException {
            String content = "Hello, S3 Integration Test!";
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            FileId fileId = FileId.generate();
            InputStream inputStream = new ByteArrayInputStream(contentBytes);
            FileSize fileSize = FileSize.of(contentBytes.length);
            MimeType mimeType = MimeType.of("text/plain");
            StorageMetadata metadata = StorageMetadata.of(
                    UUID.randomUUID().toString().replace("-", ""),
                    "hello.txt",
                    null
            );

            StorageResult result = s3StorageAdapter.store(fileId, inputStream, fileSize, mimeType, metadata);

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(result.getLocation().bucketName())
                    .key(result.getLocation().objectKey())
                    .build();

            try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest)) {
                String retrievedContent = new String(response.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(retrievedContent).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should set correct content type on S3 object")
        void shouldSetCorrectContentType() {
            String content = "{\"key\": \"value\"}";
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            FileId fileId = FileId.generate();
            InputStream inputStream = new ByteArrayInputStream(contentBytes);
            FileSize fileSize = FileSize.of(contentBytes.length);
            MimeType mimeType = MimeType.of("application/json");
            StorageMetadata metadata = StorageMetadata.of(
                    UUID.randomUUID().toString().replace("-", ""),
                    "data.json",
                    null
            );

            StorageResult result = s3StorageAdapter.store(fileId, inputStream, fileSize, mimeType, metadata);

            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(result.getLocation().bucketName())
                    .key(result.getLocation().objectKey())
                    .build();

            HeadObjectResponse headResponse = s3Client.headObject(headRequest);
            assertThat(headResponse.contentType()).isEqualTo("application/json");

            assertThat(headResponse.serverSideEncryption())
                    .as("Uploaded objects must be encrypted with SSE-KMS")
                    .isEqualTo(ServerSideEncryption.AWS_KMS);
        }

        @Test
        @DisplayName("should store custom metadata on S3 object")
        void shouldStoreCustomMetadata() {
            String content = "File with metadata";
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            FileId fileId = FileId.generate();
            InputStream inputStream = new ByteArrayInputStream(contentBytes);
            FileSize fileSize = FileSize.of(contentBytes.length);
            MimeType mimeType = MimeType.of("text/plain");
            String correlationId = "a1b2c3d4e5f67890a1b2c3d4e5f67890";
            String originalFilename = "original-file.txt";
            StorageMetadata metadata = StorageMetadata.of(
                    correlationId,
                    originalFilename,
                    "sha256checksum"
            );

            StorageResult result = s3StorageAdapter.store(fileId, inputStream, fileSize, mimeType, metadata);

            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(result.getLocation().bucketName())
                    .key(result.getLocation().objectKey())
                    .build();

            HeadObjectResponse headResponse = s3Client.headObject(headRequest);
            assertThat(headResponse.metadata()).containsEntry("correlation-id", correlationId);
            assertThat(headResponse.metadata()).containsEntry(
                    "original-filename",
                    FileName.safeForLogs(originalFilename));
        }
    }

    @Nested
    @DisplayName("download")
    class Download {

        @Test
        @DisplayName("should download existing file from S3")
        void shouldDownloadExistingFile() throws IOException {
            String content = "Content to download";
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            String objectKey = "downloads/" + UUID.randomUUID() + "/test-download.txt";

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(TEST_BUCKET)
                            .key(objectKey)
                            .contentType("text/plain")
                            .build(),
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(contentBytes)
            );

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(TEST_BUCKET)
                    .key(objectKey)
                    .build();

            try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest)) {
                String retrievedContent = new String(response.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(retrievedContent).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should throw exception for non-existent file")
        void shouldThrowExceptionForNonExistentFile() {
            String nonExistentKey = "non-existent/" + UUID.randomUUID() + "/file.txt";

            assertThatThrownBy(() -> {
                s3Client.getObject(GetObjectRequest.builder()
                        .bucket(TEST_BUCKET)
                        .key(nonExistentKey)
                        .build());
            }).isInstanceOf(NoSuchKeyException.class);
        }
    }
}
