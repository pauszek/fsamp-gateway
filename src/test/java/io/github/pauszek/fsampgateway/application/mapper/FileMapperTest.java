package io.github.pauszek.fsampgateway.application.mapper;

import io.github.pauszek.fsampgateway.application.dto.FileUploadResponseDto;
import io.github.pauszek.fsampgateway.domain.model.*;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FileMapper")
class FileMapperTest {

    private final FileMapper mapper = new FileMapper();

    @Nested
    @DisplayName("toResponseDto")
    class ToResponseDto {

        @Test
        @DisplayName("should map SecureFile to FileUploadResponseDto")
        void shouldMapSecureFileToResponseDto() {
            SecureFile file = SecureFile.createPending(
                    FileName.of("document.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(2048L),
                    CorrelationId.generate(),
                    "user-123"
            ).markAsUploaded(
                    StorageLocation.of("bucket", "key"),
                    EncryptionMetadata.kmsEncrypted("kms-key-id"),
                    Checksum.sha256("a".repeat(64))
            );

            FileUploadResponseDto result = mapper.toResponseDto(file);

            assertThat(result).isNotNull();
            assertThat(result.fileId()).isEqualTo(file.getId().value());
            assertThat(result.correlationId()).isEqualTo(file.getCorrelationId().value());
            assertThat(result.filename()).isEqualTo("document.pdf");
            assertThat(result.sizeBytes()).isEqualTo(2048L);
            assertThat(result.mimeType()).isEqualTo("application/pdf");
            assertThat(result.status()).isEqualTo("UPLOADED");
            assertThat(result.message()).isEqualTo("File uploaded successfully and queued for processing");
        }

        @Test
        @DisplayName("should map file size to human readable format")
        void shouldMapFileSizeToHumanReadable() {
            SecureFile file = SecureFile.createPending(
                    FileName.of("large-file.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(1024L * 1024L * 5), // 5 MB
                    CorrelationId.generate(),
                    "user-123"
            ).markAsUploaded(
                    StorageLocation.of("bucket", "key"),
                    EncryptionMetadata.kmsEncrypted("kms-key-id"),
                    Checksum.sha256("b".repeat(64))
            );

            FileUploadResponseDto result = mapper.toResponseDto(file);

            assertThat(result.sizeHuman()).isEqualTo("5.00 MB");
        }

        @Test
        @DisplayName("should handle pending file without checksum")
        void shouldHandlePendingFileWithoutChecksum() {
            SecureFile file = SecureFile.createPending(
                    FileName.of("pending.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(1024L),
                    CorrelationId.generate(),
                    "user-123"
            );

            FileUploadResponseDto result = mapper.toResponseDto(file);

            assertThat(result).isNotNull();
            assertThat(result.checksum()).isNull();
            assertThat(result.status()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("should include status description")
        void shouldIncludeStatusDescription() {
            SecureFile file = SecureFile.createPending(
                    FileName.of("test.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(1024L),
                    CorrelationId.generate(),
                    "user-123"
            ).markAsUploaded(
                    StorageLocation.of("bucket", "key"),
                    EncryptionMetadata.kmsEncrypted("kms-key-id"),
                    Checksum.sha256("c".repeat(64))
            );

            FileUploadResponseDto result = mapper.toResponseDto(file);

            assertThat(result.statusDescription()).isNotBlank();
        }

        @Test
        @DisplayName("should include checksum for uploaded file")
        void shouldIncludeChecksumForUploadedFile() {
            String expectedChecksum = "d".repeat(64);
            SecureFile file = SecureFile.createPending(
                    FileName.of("test.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(1024L),
                    CorrelationId.generate(),
                    "user-123"
            ).markAsUploaded(
                    StorageLocation.of("bucket", "key"),
                    EncryptionMetadata.kmsEncrypted("kms-key-id"),
                    Checksum.sha256(expectedChecksum)
            );

            FileUploadResponseDto result = mapper.toResponseDto(file);

            assertThat(result.checksum()).isEqualTo(expectedChecksum);
        }

        @Test
        @DisplayName("should include upload timestamp")
        void shouldIncludeUploadTimestamp() {
            SecureFile file = SecureFile.createPending(
                    FileName.of("test.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(1024L),
                    CorrelationId.generate(),
                    "user-123"
            ).markAsUploaded(
                    StorageLocation.of("bucket", "key"),
                    EncryptionMetadata.kmsEncrypted("kms-key-id"),
                    Checksum.sha256("e".repeat(64))
            );

            FileUploadResponseDto result = mapper.toResponseDto(file);

            assertThat(result.uploadedAt()).isNotNull();
        }
    }
}
