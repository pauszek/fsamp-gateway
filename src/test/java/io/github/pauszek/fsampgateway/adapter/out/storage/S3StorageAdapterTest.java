package io.github.pauszek.fsampgateway.adapter.out.storage;

import io.github.pauszek.fsampgateway.domain.exception.FileNotFoundException;
import io.github.pauszek.fsampgateway.domain.exception.StorageConfigurationException;
import io.github.pauszek.fsampgateway.domain.exception.StorageException;
import io.github.pauszek.fsampgateway.domain.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3StorageAdapter")
class S3StorageAdapterTest {

    @Mock
    private S3Client s3Client;

    private S3StorageProperties properties;

    private S3StorageAdapter adapter;

    private static final String BUCKET_NAME = "test-bucket";
    private static final String KMS_KEY_ID = "alias/test-key";
    private static final byte[] TEST_CONTENT = "Test file content".getBytes();

    @BeforeEach
    void setUp() {
        properties = new S3StorageProperties();
        properties.setBucketName(BUCKET_NAME);
        properties.setKmsKeyId(KMS_KEY_ID);
        adapter = new S3StorageAdapter(s3Client, properties);
    }

    @Nested
    @DisplayName("store")
    class Store {

        @Captor
        private ArgumentCaptor<PutObjectRequest> requestCaptor;

        @Test
        @DisplayName("should store file with KMS encryption")
        void shouldStoreFileWithKmsEncryption() {
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("a1b2c3d4e5f67890a1b2c3d4e5f67890", "test.pdf", "checksum123");

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("etag-123").build());

            adapter.store(fileId, content, size, mimeType, metadata);

            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));
            PutObjectRequest request = requestCaptor.getValue();

            assertThat(request.bucket()).isEqualTo(BUCKET_NAME);
            assertThat(request.serverSideEncryption()).isEqualTo(ServerSideEncryption.AWS_KMS);
            assertThat(request.ssekmsKeyId()).isEqualTo(KMS_KEY_ID);
            assertThat(request.contentType()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("should include original filename metadata in request")
        void shouldIncludeMetadataInRequest() {
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("a1b2c3d4e5f67890a1b2c3d4e5f67890", "test-file.pdf", "sha256hash");

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("etag-123").build());

            adapter.store(fileId, content, size, mimeType, metadata);

            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));
            var s3Metadata = requestCaptor.getValue().metadata();

            assertThat(s3Metadata)
                    .containsEntry("correlation-id", "a1b2c3d4e5f67890a1b2c3d4e5f67890")
                    .containsEntry("original-filename", "test-file.pdf")
                    .containsEntry("checksum-sha256", "sha256hash");
        }

        @Test
        @DisplayName("should generate correct object key format")
        void shouldGenerateCorrectObjectKeyFormat() {
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("b1c2d3e4f5a67890b1c2d3e4f5a67890", "test.pdf", null);

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("etag-123").build());

            adapter.store(fileId, content, size, mimeType, metadata);

            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));
            String key = requestCaptor.getValue().key();

            assertThat(key)
                    .matches("uploads/\\d{4}/\\d{2}/\\d{2}/.*")
                    .contains(fileId.value().toString());
        }

        @Test
        @DisplayName("should return StorageResult with location and encryption metadata")
        void shouldReturnStorageResult() {
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("c1d2e3f4a5b67890c1d2e3f4a5b67890", "test.pdf", null);

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("\"etag-123\"").build());

            StorageResult result = adapter.store(fileId, content, size, mimeType, metadata);

            assertThat(result)
                    .satisfies(storageResult -> {
                        assertThat(storageResult.getLocation().bucketName()).isEqualTo(BUCKET_NAME);
                        assertThat(storageResult.getEncryptionMetadata().kmsKeyId()).isEqualTo(KMS_KEY_ID);
                        assertThat(storageResult.getEtag()).isEqualTo("\"etag-123\"");
                    });
        }

        @Test
        @DisplayName("should throw StorageException on S3Exception")
        void shouldThrowStorageExceptionOnS3Error() {
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("d1e2f3a4b5c67890d1e2f3a4b5c67890", "test.pdf", null);

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willThrow(S3Exception.builder().message("Access Denied").build());

            assertThatThrownBy(() -> adapter.store(fileId, content, size, mimeType, metadata))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("Failed to store file in S3");
        }

        @Test
        @DisplayName("should sanitize non-ASCII filename in metadata")
        void shouldSanitizeNonAsciiFilenameInMetadata() {
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("a1b2c3d4e5f67890a1b2c3d4e5f67890", "plik-żółć.pdf", null);

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("etag-123").build());

            adapter.store(fileId, content, size, mimeType, metadata);

            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));
            String originalFilename = requestCaptor.getValue().metadata().get("original-filename");
            assertThat(originalFilename).doesNotContain("ż", "ó", "ł", "ć");
            assertThat(originalFilename).isEqualTo("plik-____.pdf");
        }

        @Test
        @DisplayName("should handle null original filename in metadata")
        void shouldHandleNullOriginalFilename() {
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("a1b2c3d4e5f67890a1b2c3d4e5f67890", null, null);

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("etag-123").build());

            adapter.store(fileId, content, size, mimeType, metadata);

            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));
            String originalFilename = requestCaptor.getValue().metadata().get("original-filename");
            assertThat(originalFilename).isEqualTo("<unknown>");
        }

        @Test
        @DisplayName("should fail fast when no KMS key is configured")
        void shouldFailFastWhenNoKmsKeyConfigured() {
            S3StorageProperties noKeyProps = new S3StorageProperties();
            noKeyProps.setBucketName(BUCKET_NAME);

            S3StorageAdapter adapterWithoutKey = new S3StorageAdapter(s3Client, noKeyProps);

            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("a1b2c3d4e5f67890a1b2c3d4e5f67890", "test.pdf", null);

            assertThatThrownBy(() -> adapterWithoutKey.store(fileId, content, size, mimeType, metadata))
                    .isInstanceOf(StorageConfigurationException.class)
                    .hasMessageContaining("KMS key id is required");
        }

        @Test
        @DisplayName("should use fallback KMS key when properties key is null")
        void shouldUseFallbackKmsKeyWhenPropertiesKeyIsNull() {
            S3StorageProperties nullKeyProps = new S3StorageProperties();
            nullKeyProps.setBucketName(BUCKET_NAME);
            nullKeyProps.setKmsKeyId(null);
            
            S3StorageAdapter adapterWithNullKey = new S3StorageAdapter(s3Client, nullKeyProps);
            org.springframework.test.util.ReflectionTestUtils.setField(
                    adapterWithNullKey, "fallbackKmsKeyId", "alias/fallback-key");

            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("a1b2c3d4e5f67890a1b2c3d4e5f67890", "test.pdf", null);

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("etag-123").build());

            adapterWithNullKey.store(fileId, content, size, mimeType, metadata);

            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));
            assertThat(requestCaptor.getValue().ssekmsKeyId()).isEqualTo("alias/fallback-key");
        }

        @Test
        @DisplayName("should use fallback KMS key when properties key is blank")
        void shouldUseFallbackKmsKeyWhenPropertiesKeyIsBlank() {
            S3StorageProperties blankKeyProps = new S3StorageProperties();
            blankKeyProps.setBucketName(BUCKET_NAME);
            blankKeyProps.setKmsKeyId("   ");
            
            S3StorageAdapter adapterWithBlankKey = new S3StorageAdapter(s3Client, blankKeyProps);
            org.springframework.test.util.ReflectionTestUtils.setField(
                    adapterWithBlankKey, "fallbackKmsKeyId", "alias/fallback-key");

            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("a1b2c3d4e5f67890a1b2c3d4e5f67890", "test.pdf", null);

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("etag-123").build());

            adapterWithBlankKey.store(fileId, content, size, mimeType, metadata);

            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));
            assertThat(requestCaptor.getValue().ssekmsKeyId()).isEqualTo("alias/fallback-key");
        }
    }

    @Nested
    @DisplayName("retrieve")
    class Retrieve {

        @Test
        @DisplayName("should retrieve file content")
        void shouldRetrieveFileContent() {
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            var mockResponse = mock(software.amazon.awssdk.core.ResponseInputStream.class);
            given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(mockResponse);

            InputStream result = adapter.retrieve(location);

            assertThat(result).isNotNull();
            then(s3Client).should().getObject(argThat((GetObjectRequest req) ->
                    req.bucket().equals(BUCKET_NAME) && req.key().equals("uploads/2024/01/01/file-id")
            ));
        }

        @Test
        @DisplayName("should throw FileNotFoundException for NoSuchKeyException")
        void shouldThrowFileNotFoundForNoSuchKey() {
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.getObject(any(GetObjectRequest.class)))
                    .willThrow(NoSuchKeyException.builder().message("Key not found").build());

            assertThatThrownBy(() -> adapter.retrieve(location))
                    .isInstanceOf(FileNotFoundException.class)
                    .hasMessageContaining("File not found");
        }

        @Test
        @DisplayName("should throw StorageException on S3Exception")
        void shouldThrowStorageExceptionOnRetrieveError() {
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.getObject(any(GetObjectRequest.class)))
                    .willThrow(S3Exception.builder().message("Access Denied").build());

            assertThatThrownBy(() -> adapter.retrieve(location))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("Failed to retrieve file from S3");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should delete file successfully")
        void shouldDeleteFileSuccessfully() {
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .willReturn(DeleteObjectResponse.builder().build());

            adapter.delete(location);

            then(s3Client).should().deleteObject(argThat((DeleteObjectRequest req) ->
                    req.bucket().equals(BUCKET_NAME) && req.key().equals("uploads/2024/01/01/file-id")
            ));
        }

        @Test
        @DisplayName("should throw StorageException on delete error")
        void shouldThrowStorageExceptionOnDeleteError() {
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .willThrow(S3Exception.builder().message("Access Denied").build());

            assertThatThrownBy(() -> adapter.delete(location))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("Failed to delete file from S3");
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("should return true when file exists")
        void shouldReturnTrueWhenFileExists() {
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.headObject(any(HeadObjectRequest.class)))
                    .willReturn(HeadObjectResponse.builder().build());

            boolean result = adapter.exists(location);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when file does not exist")
        void shouldReturnFalseWhenFileDoesNotExist() {
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.headObject(any(HeadObjectRequest.class)))
                    .willThrow(NoSuchKeyException.builder().message("Key not found").build());

            boolean result = adapter.exists(location);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should throw StorageException on other S3 errors")
        void shouldThrowStorageExceptionOnOtherErrors() {
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.headObject(any(HeadObjectRequest.class)))
                    .willThrow(S3Exception.builder().message("Internal Server Error").build());

            assertThatThrownBy(() -> adapter.exists(location))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("Failed to check file existence");
        }
    }
}
