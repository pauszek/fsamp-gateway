package io.github.pauszek.fsampgateway.adapter.out.storage;

import io.github.pauszek.fsampgateway.domain.exception.FileNotFoundException;
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
import java.time.LocalDate;
import java.util.UUID;

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
            // given
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("a1b2c3d4e5f67890a1b2c3d4e5f67890", "test.pdf", "checksum123");

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("etag-123").build());

            // when
            StorageResult result = adapter.store(fileId, content, size, mimeType, metadata);

            // then
            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));
            PutObjectRequest request = requestCaptor.getValue();

            assertThat(request.bucket()).isEqualTo(BUCKET_NAME);
            assertThat(request.serverSideEncryption()).isEqualTo(ServerSideEncryption.AWS_KMS);
            assertThat(request.ssekmsKeyId()).isEqualTo(KMS_KEY_ID);
            assertThat(request.contentType()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("should include metadata in request")
        void shouldIncludeMetadataInRequest() {
            // given
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("a1b2c3d4e5f67890a1b2c3d4e5f67890", "test-file.pdf", "sha256hash");

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("etag-123").build());

            // when
            adapter.store(fileId, content, size, mimeType, metadata);

            // then
            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));
            var s3Metadata = requestCaptor.getValue().metadata();

            assertThat(s3Metadata).containsEntry("correlation-id", "a1b2c3d4e5f67890a1b2c3d4e5f67890");
            assertThat(s3Metadata).containsEntry("original-filename", "test-file.pdf");
            assertThat(s3Metadata).containsEntry("checksum-sha256", "sha256hash");
        }

        @Test
        @DisplayName("should generate correct object key format")
        void shouldGenerateCorrectObjectKeyFormat() {
            // given
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("b1c2d3e4f5a67890b1c2d3e4f5a67890", "test.pdf", null);

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("etag-123").build());

            // when
            adapter.store(fileId, content, size, mimeType, metadata);

            // then
            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));
            String key = requestCaptor.getValue().key();

            LocalDate today = LocalDate.now();
            String expectedPrefix = String.format("uploads/%d/%02d/%02d/",
                    today.getYear(), today.getMonthValue(), today.getDayOfMonth());
            assertThat(key).startsWith(expectedPrefix);
            assertThat(key).contains(fileId.value().toString());
        }

        @Test
        @DisplayName("should return StorageResult with location and encryption metadata")
        void shouldReturnStorageResult() {
            // given
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("c1d2e3f4a5b67890c1d2e3f4a5b67890", "test.pdf", null);

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("\"etag-123\"").build());

            // when
            StorageResult result = adapter.store(fileId, content, size, mimeType, metadata);

            // then
            assertThat(result.getLocation().bucketName()).isEqualTo(BUCKET_NAME);
            assertThat(result.getEncryptionMetadata().kmsKeyId()).isEqualTo(KMS_KEY_ID);
            assertThat(result.getEtag()).isEqualTo("\"etag-123\"");
        }

        @Test
        @DisplayName("should throw StorageException on S3Exception")
        void shouldThrowStorageExceptionOnS3Error() {
            // given
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("d1e2f3a4b5c67890d1e2f3a4b5c67890", "test.pdf", null);

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willThrow(S3Exception.builder().message("Access Denied").build());

            // when/then
            assertThatThrownBy(() -> adapter.store(fileId, content, size, mimeType, metadata))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("Failed to store file in S3");
        }

        @Test
        @DisplayName("should sanitize non-ASCII metadata values")
        void shouldSanitizeNonAsciiMetadataValues() {
            // given
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("a1b2c3d4e5f67890a1b2c3d4e5f67890", "plik-żółć.pdf", null);

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("etag-123").build());

            // when
            adapter.store(fileId, content, size, mimeType, metadata);

            // then
            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));
            String originalFilename = requestCaptor.getValue().metadata().get("original-filename");
            assertThat(originalFilename).doesNotContain("ż", "ó", "ł", "ć");
            assertThat(originalFilename).contains("plik-", ".pdf");
        }

        @Test
        @DisplayName("should handle null original filename in metadata")
        void shouldHandleNullOriginalFilename() {
            // given
            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("a1b2c3d4e5f67890a1b2c3d4e5f67890", null, null);

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("etag-123").build());

            // when
            adapter.store(fileId, content, size, mimeType, metadata);

            // then
            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));
            String originalFilename = requestCaptor.getValue().metadata().get("original-filename");
            assertThat(originalFilename).isEqualTo("");
        }

        @Test
        @DisplayName("should use fallback KMS key when properties key is null")
        void shouldUseFallbackKmsKeyWhenPropertiesKeyIsNull() {
            // given
            S3StorageProperties nullKeyProps = new S3StorageProperties();
            nullKeyProps.setBucketName(BUCKET_NAME);
            nullKeyProps.setKmsKeyId(null);
            
            S3StorageAdapter adapterWithNullKey = new S3StorageAdapter(s3Client, nullKeyProps);
            // Use reflection to set fallbackKmsKeyId
            org.springframework.test.util.ReflectionTestUtils.setField(
                    adapterWithNullKey, "fallbackKmsKeyId", "alias/fallback-key");

            FileId fileId = FileId.generate();
            InputStream content = new ByteArrayInputStream(TEST_CONTENT);
            FileSize size = FileSize.of(TEST_CONTENT.length);
            MimeType mimeType = MimeType.of("application/pdf");
            StorageMetadata metadata = StorageMetadata.of("a1b2c3d4e5f67890a1b2c3d4e5f67890", "test.pdf", null);

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(PutObjectResponse.builder().eTag("etag-123").build());

            // when
            adapterWithNullKey.store(fileId, content, size, mimeType, metadata);

            // then
            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));
            assertThat(requestCaptor.getValue().ssekmsKeyId()).isEqualTo("alias/fallback-key");
        }

        @Test
        @DisplayName("should use fallback KMS key when properties key is blank")
        void shouldUseFallbackKmsKeyWhenPropertiesKeyIsBlank() {
            // given
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

            // when
            adapterWithBlankKey.store(fileId, content, size, mimeType, metadata);

            // then
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
            // given
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            var mockResponse = mock(software.amazon.awssdk.core.ResponseInputStream.class);
            given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(mockResponse);

            // when
            InputStream result = adapter.retrieve(location);

            // then
            assertThat(result).isNotNull();
            then(s3Client).should().getObject(argThat((GetObjectRequest req) ->
                    req.bucket().equals(BUCKET_NAME) && req.key().equals("uploads/2024/01/01/file-id")
            ));
        }

        @Test
        @DisplayName("should throw FileNotFoundException for NoSuchKeyException")
        void shouldThrowFileNotFoundForNoSuchKey() {
            // given
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.getObject(any(GetObjectRequest.class)))
                    .willThrow(NoSuchKeyException.builder().message("Key not found").build());

            // when/then
            assertThatThrownBy(() -> adapter.retrieve(location))
                    .isInstanceOf(FileNotFoundException.class)
                    .hasMessageContaining("File not found");
        }

        @Test
        @DisplayName("should throw StorageException on S3Exception")
        void shouldThrowStorageExceptionOnRetrieveError() {
            // given
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.getObject(any(GetObjectRequest.class)))
                    .willThrow(S3Exception.builder().message("Access Denied").build());

            // when/then
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
            // given
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .willReturn(DeleteObjectResponse.builder().build());

            // when
            adapter.delete(location);

            // then
            then(s3Client).should().deleteObject(argThat((DeleteObjectRequest req) ->
                    req.bucket().equals(BUCKET_NAME) && req.key().equals("uploads/2024/01/01/file-id")
            ));
        }

        @Test
        @DisplayName("should throw StorageException on delete error")
        void shouldThrowStorageExceptionOnDeleteError() {
            // given
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .willThrow(S3Exception.builder().message("Access Denied").build());

            // when/then
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
            // given
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.headObject(any(HeadObjectRequest.class)))
                    .willReturn(HeadObjectResponse.builder().build());

            // when
            boolean result = adapter.exists(location);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when file does not exist")
        void shouldReturnFalseWhenFileDoesNotExist() {
            // given
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.headObject(any(HeadObjectRequest.class)))
                    .willThrow(NoSuchKeyException.builder().message("Key not found").build());

            // when
            boolean result = adapter.exists(location);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should throw StorageException on other S3 errors")
        void shouldThrowStorageExceptionOnOtherErrors() {
            // given
            StorageLocation location = StorageLocation.of(BUCKET_NAME, "uploads/2024/01/01/file-id");
            given(s3Client.headObject(any(HeadObjectRequest.class)))
                    .willThrow(S3Exception.builder().message("Internal Server Error").build());

            // when/then
            assertThatThrownBy(() -> adapter.exists(location))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("Failed to check file existence");
        }
    }
}
