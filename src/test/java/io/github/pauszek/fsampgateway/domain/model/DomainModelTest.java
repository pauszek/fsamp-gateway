package io.github.pauszek.fsampgateway.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Domain Model Tests")
class DomainModelTest {

    @Nested
    @DisplayName("FileId Value Object")
    class FileIdTests {

        @Test
        @DisplayName("should generate unique IDs")
        void shouldGenerateUniqueIds() {
            FileId id1 = FileId.generate();
            FileId id2 = FileId.generate();
            
            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("should create from valid UUID string")
        void shouldCreateFromValidUuidString() {
            String uuidString = "550e8400-e29b-41d4-a716-446655440000";
            FileId fileId = FileId.of(uuidString);
            
            assertThat(fileId.value().toString()).isEqualTo(uuidString);
        }

        @Test
        @DisplayName("should throw for invalid UUID string")
        void shouldThrowForInvalidUuidString() {
            assertThatThrownBy(() -> FileId.of("invalid"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("FileName Value Object")
    class FileNameTests {

        @Test
        @DisplayName("should create valid filename")
        void shouldCreateValidFilename() {
            FileName fileName = FileName.of("document.pdf");
            
            assertThat(fileName.value()).isEqualTo("document.pdf");
            assertThat(fileName.getExtension()).isEqualTo("pdf");
            assertThat(fileName.getBaseName()).isEqualTo("document");
        }

        @Test
        @DisplayName("should reject path traversal attempts")
        void shouldRejectPathTraversalAttempts() {
            assertThatThrownBy(() -> FileName.of("../../../etc/passwd"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("path traversal");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("should reject blank filenames")
        void shouldRejectBlankFilenames(String invalidName) {
            assertThatThrownBy(() -> FileName.of(invalidName))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("MimeType Value Object")
    class MimeTypeTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "application/pdf",
                "image/png",
                "image/jpeg",
                "application/json",
                "text/plain"
        })
        @DisplayName("should accept allowed MIME types")
        void shouldAcceptAllowedMimeTypes(String mimeType) {
            MimeType type = MimeType.of(mimeType);
            
            assertThat(type.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should reject executable MIME types")
        void shouldRejectExecutableMimeTypes() {
            MimeType type = MimeType.of("application/x-msdownload");
            
            assertThat(type.isAllowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("FileSize Value Object")
    class FileSizeTests {

        @Test
        @DisplayName("should validate maximum size")
        void shouldValidateMaximumSize() {
            assertThatThrownBy(() -> FileSize.of(200 * 1024 * 1024)) // 200MB
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds maximum");
        }

        @Test
        @DisplayName("should format human readable size")
        void shouldFormatHumanReadableSize() {
            FileSize size = FileSize.of(1024 * 1024); // 1MB
            
            assertThat(size.toHumanReadable()).isEqualTo("1.00 MB");
        }

        @Test
        @DisplayName("should reject zero or negative size")
        void shouldRejectZeroOrNegativeSize() {
            assertThatThrownBy(() -> FileSize.of(0))
                    .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> FileSize.of(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("SecureFile Aggregate Root")
    class SecureFileTests {

        @Test
        @DisplayName("should create file in PENDING status")
        void shouldCreateFileInPendingStatus() {
            SecureFile file = createTestFile();
            
            assertThat(file.getStatus()).isEqualTo(FileStatus.PENDING);
        }

        @Test
        @DisplayName("should transition from PENDING to UPLOADED")
        void shouldTransitionToUploaded() {
            SecureFile file = createTestFile();
            StorageLocation location = new StorageLocation(
                    "test-bucket", "uploads/test.pdf"
            );
            EncryptionMetadata encryption = EncryptionMetadata.kmsEncrypted("alias/test-key");
            Checksum checksum = Checksum.sha256("a".repeat(64));
            
            SecureFile uploadedFile = file.markAsUploaded(location, encryption, checksum);
            
            assertThat(uploadedFile.getStatus()).isEqualTo(FileStatus.UPLOADED);
            assertThat(uploadedFile.getStorageLocation()).isEqualTo(location);
        }

        @Test
        @DisplayName("should not allow invalid state transitions")
        void shouldNotAllowInvalidStateTransitions() {
            SecureFile file = createTestFile();
            
            assertThatThrownBy(file::markAsCompleted)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot complete");
        }

        @Test
        @DisplayName("should support UPLOADED to PROCESSING transition")
        void shouldSupportUploadedToProcessingTransition() {
            SecureFile file = createUploadedFile();
            
            SecureFile processingFile = file.markAsProcessing();
            
            assertThat(processingFile.getStatus()).isEqualTo(FileStatus.PROCESSING);
        }

        @Test
        @DisplayName("should support failure from any active state")
        void shouldSupportFailureFromAnyActiveState() {
            SecureFile file = createTestFile();
            
            SecureFile failedFile = file.markAsFailed();
            
            assertThat(failedFile.getStatus()).isEqualTo(FileStatus.FAILED);
        }

        private SecureFile createTestFile() {
            return SecureFile.builder()
                    .id(FileId.generate())
                    .correlationId(CorrelationId.generate())
                    .fileName(FileName.of("test.pdf"))
                    .mimeType(MimeType.of("application/pdf"))
                    .size(FileSize.of(1024))
                    .status(FileStatus.PENDING)
                    .auditInfo(AuditInfo.create("testuser"))
                    .build();
        }

        private SecureFile createUploadedFile() {
            SecureFile file = createTestFile();
            return file.markAsUploaded(
                    new StorageLocation("bucket", "key"),
                    EncryptionMetadata.kmsEncrypted("keyId"),
                    Checksum.sha256("b".repeat(64))  // Valid 64 hex chars
            );
        }
    }
}
