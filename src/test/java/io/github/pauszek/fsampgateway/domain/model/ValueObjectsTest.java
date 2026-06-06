package io.github.pauszek.fsampgateway.domain.model;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Value Objects Tests")
class ValueObjectsTest {

    private static final Instant TEST_ISSUED_AT = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant TEST_EXPIRES_AT = Instant.parse("2026-01-15T11:00:00Z");

    @Nested
    @DisplayName("MimeType Value Object")
    class MimeTypeTests {

        @Test
        @DisplayName("should normalize MIME type to lowercase")
        void shouldNormalizeToLowercase() {
            MimeType type = MimeType.of("APPLICATION/PDF");
            
            assertThat(type.value()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("should detect image types")
        void shouldDetectImageTypes() {
            assertThat(MimeType.of("image/png").isImage()).isTrue();
            assertThat(MimeType.of("image/jpeg").isImage()).isTrue();
            assertThat(MimeType.of("image/gif").isImage()).isTrue();
            assertThat(MimeType.of("application/pdf").isImage()).isFalse();
        }

        @Test
        @DisplayName("should detect document types")
        void shouldDetectDocumentTypes() {
            assertThat(MimeType.of("application/pdf").isDocument()).isTrue();
            assertThat(MimeType.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").isDocument()).isTrue();
            assertThat(MimeType.of("text/plain").isDocument()).isFalse();
        }

        @Test
        @DisplayName("should throw for null MIME type")
        void shouldThrowForNull() {
            assertThatThrownBy(() -> MimeType.of(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw for blank MIME type")
        void shouldThrowForBlank() {
            assertThatThrownBy(() -> MimeType.of("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            MimeType type = MimeType.of("text/plain");
            assertThat(type.toString()).isEqualTo("text/plain");
        }
    }

    @Nested
    @DisplayName("FileName Value Object")
    class FileNameTests {

        @Test
        @DisplayName("should return empty extension for file without extension")
        void shouldReturnEmptyExtensionForFileWithoutExtension() {
            FileName fileName = FileName.of("README");
            
            assertThat(fileName.getExtension()).isEmpty();
            assertThat(fileName.getBaseName()).isEqualTo("README");
        }

        @Test
        @DisplayName("should handle file starting with dot")
        void shouldHandleFileStartingWithDot() {
            FileName fileName = FileName.of(".gitignore");
            
            assertThat(fileName.getExtension()).isEmpty();
            assertThat(fileName.getBaseName()).isEqualTo(".gitignore");
        }

        @Test
        @DisplayName("should handle multiple dots in filename")
        void shouldHandleMultipleDots() {
            FileName fileName = FileName.of("archive.tar.gz");
            
            assertThat(fileName.getExtension()).isEqualTo("gz");
            assertThat(fileName.getBaseName()).isEqualTo("archive.tar");
        }

        @Test
        @DisplayName("should throw for null filename")
        void shouldThrowForNull() {
            assertThatThrownBy(() -> FileName.of(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw for filename exceeding max length")
        void shouldThrowForExceedingMaxLength() {
            String longName = "a".repeat(256);
            
            assertThatThrownBy(() -> FileName.of(longName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds maximum length");
        }

        @ParameterizedTest
        @ValueSource(strings = {"file<name>", "file:name", "file*name", "file?name", "file\"name", "file|name"})
        @DisplayName("should throw for invalid characters")
        void shouldThrowForInvalidCharacters(String invalidName) {
            assertThatThrownBy(() -> FileName.of(invalidName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid characters");
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            FileName fileName = FileName.of("test.pdf");
            assertThat(fileName.toString()).isEqualTo("test.pdf");
        }

        @Test
        @DisplayName("should provide privacy-safe filename for logs")
        void shouldProvidePrivacySafeFilenameForLogs() {
            FileName fileName = FileName.of("Jane_Doe_tax_return.pdf");

            assertThat(fileName.redactedForLogs()).isEqualTo("<redacted len=23 ext=.pdf>");
            assertThat(FileName.redactedForLogs(null)).isEqualTo("<unknown>");
        }

        @Test
        @DisplayName("should sanitize unsafe extension when redacting raw filename")
        void shouldSanitizeUnsafeExtensionWhenRedactingRawFilename() {
            assertThat(FileName.redactedForLogs("invoice.\nINFO")).isEqualTo("<redacted len=13 ext=>");
            assertThat(FileName.redactedForLogs("report.PDF")).isEqualTo("<redacted len=10 ext=.pdf>");
        }
    }

    @Nested
    @DisplayName("FileSize Value Object")
    class FileSizeTests {

        @Test
        @DisplayName("should format bytes correctly")
        void shouldFormatBytesCorrectly() {
            assertThat(FileSize.of(512).toHumanReadable()).isEqualTo("512 B");
        }

        @Test
        @DisplayName("should format kilobytes correctly")
        void shouldFormatKilobytesCorrectly() {
            assertThat(FileSize.of(2048).toHumanReadable()).isEqualTo("2.00 KB");
        }

        @Test
        @DisplayName("should format megabytes correctly")
        void shouldFormatMegabytesCorrectly() {
            assertThat(FileSize.of(10 * 1024 * 1024).toHumanReadable()).isEqualTo("10.00 MB");
        }

        @Test
        @DisplayName("should throw for size exceeding max")
        void shouldThrowForSizeExceedingMax() {
            assertThatThrownBy(() -> FileSize.of(101 * 1024 * 1024))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds maximum");
        }

        @Test
        @DisplayName("should return bytes value")
        void shouldReturnBytesValue() {
            FileSize size = FileSize.of(5000L);
            assertThat(size.bytes()).isEqualTo(5000L);
        }
    }

    @Nested
    @DisplayName("CorrelationId Value Object")
    class CorrelationIdTests {

        @Test
        @DisplayName("should generate valid correlation ID")
        void shouldGenerateValidCorrelationId() {
            CorrelationId id = CorrelationId.generate();
            
            assertThat(id.value()).hasSize(32);
            assertThat(id.value()).matches("^[a-f0-9]{32}$");
        }

        @Test
        @DisplayName("should normalize to lowercase")
        void shouldNormalizeToLowercase() {
            CorrelationId id = CorrelationId.of("A1B2C3D4E5F67890A1B2C3D4E5F67890");
            
            assertThat(id.value()).isEqualTo("a1b2c3d4e5f67890a1b2c3d4e5f67890");
        }

        @Test
        @DisplayName("should generate new ID for null input")
        void shouldGenerateNewIdForNull() {
            CorrelationId id = CorrelationId.of(null);
            
            assertThat(id.value()).isNotNull().hasSize(32);
        }

        @Test
        @DisplayName("should generate new ID for blank input")
        void shouldGenerateNewIdForBlank() {
            CorrelationId id = CorrelationId.of("   ");
            
            assertThat(id.value()).isNotNull().hasSize(32);
        }

        @Test
        @DisplayName("should throw for invalid format")
        void shouldThrowForInvalidFormat() {
            assertThatThrownBy(() -> CorrelationId.of("not-a-valid-hex"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32-character hex string");
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            CorrelationId id = CorrelationId.of("a1b2c3d4e5f67890a1b2c3d4e5f67890");
            assertThat(id.toString()).isEqualTo("a1b2c3d4e5f67890a1b2c3d4e5f67890");
        }
    }

    @Nested
    @DisplayName("Checksum Value Object")
    class ChecksumTests {

        @Test
        @DisplayName("should create SHA-256 checksum")
        void shouldCreateSha256Checksum() {
            Checksum checksum = Checksum.sha256("a".repeat(64));
            
            assertThat(checksum.value()).isEqualTo("a".repeat(64));
            assertThat(checksum.algorithm()).isEqualTo(Checksum.Algorithm.SHA256);
        }

        @Test
        @DisplayName("should throw for invalid SHA-256 length")
        void shouldThrowForInvalidLength() {
            assertThatThrownBy(() -> Checksum.sha256("tooshort"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("64");
        }

        @Test
        @DisplayName("should throw for null checksum")
        void shouldThrowForNull() {
            assertThatThrownBy(() -> Checksum.sha256(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("StorageLocation Value Object")
    class StorageLocationTests {

        @Test
        @DisplayName("should create storage location")
        void shouldCreateStorageLocation() {
            StorageLocation location = StorageLocation.of("my-bucket", "uploads/file.pdf");
            
            assertThat(location.bucketName()).isEqualTo("my-bucket");
            assertThat(location.objectKey()).isEqualTo("uploads/file.pdf");
        }

        @Test
        @DisplayName("should throw for blank bucket name")
        void shouldThrowForBlankBucket() {
            assertThatThrownBy(() -> StorageLocation.of("", "key"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw for blank object key")
        void shouldThrowForBlankKey() {
            assertThatThrownBy(() -> StorageLocation.of("bucket", ""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("EncryptionMetadata Value Object")
    class EncryptionMetadataTests {

        @Test
        @DisplayName("should create KMS encrypted metadata")
        void shouldCreateKmsEncrypted() {
            EncryptionMetadata metadata = EncryptionMetadata.kmsEncrypted("alias/my-key");
            
            assertThat(metadata.kmsKeyId()).isEqualTo("alias/my-key");
            assertThat(metadata.encrypted()).isTrue();
            assertThat(metadata.getAlgorithmName()).isEqualTo("AES/GCM/NoPadding");
        }

        @Test
        @DisplayName("should throw for null KMS key ID")
        void shouldThrowForNullKmsKeyId() {
            assertThatThrownBy(() -> EncryptionMetadata.kmsEncrypted(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("ValidationResult")
    class ValidationResultTests {

        @Test
        @DisplayName("should create valid result")
        void shouldCreateValidResult() {
            ValidationResult result = ValidationResult.valid(MimeType.of("text/plain"));
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.isInvalid()).isFalse();
            assertThat(result.getDetectedType().value()).isEqualTo("text/plain");
            assertThat(result.getMessage()).isNull();
        }

        @Test
        @DisplayName("should create invalid result")
        void shouldCreateInvalidResult() {
            ValidationResult result = ValidationResult.invalid(
                    MimeType.of("application/x-msdownload"), 
                    "Executable files not allowed"
            );
            
            assertThat(result.isValid()).isFalse();
            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Executable files not allowed");
        }
    }

    @Nested
    @DisplayName("StorageResult")
    class StorageResultTests {

        @Test
        @DisplayName("should create storage result")
        void shouldCreateStorageResult() {
            StorageLocation location = StorageLocation.of("bucket", "key");
            EncryptionMetadata encryption = EncryptionMetadata.kmsEncrypted("key-id");
            
            StorageResult result = StorageResult.of(location, encryption, "etag-123");
            
            assertThat(result.getLocation()).isEqualTo(location);
            assertThat(result.getEncryptionMetadata()).isEqualTo(encryption);
            assertThat(result.getEtag()).isEqualTo("etag-123");
        }
    }

    @Nested
    @DisplayName("StorageMetadata")
    class StorageMetadataTests {

        @Test
        @DisplayName("should create storage metadata")
        void shouldCreateStorageMetadata() {
            StorageMetadata metadata = StorageMetadata.of(
                    "a1b2c3d4e5f67890a1b2c3d4e5f67890",
                    "document.pdf",
                    "checksum123"
            );
            
            assertThat(metadata.getCorrelationId()).isEqualTo("a1b2c3d4e5f67890a1b2c3d4e5f67890");
            assertThat(metadata.getOriginalFilename()).isEqualTo("document.pdf");
            assertThat(metadata.getChecksum()).isEqualTo("checksum123");
        }

        @Test
        @DisplayName("should handle null checksum")
        void shouldHandleNullChecksum() {
            StorageMetadata metadata = StorageMetadata.of(
                    "a1b2c3d4e5f67890a1b2c3d4e5f67890",
                    "document.pdf",
                    null
            );
            
            assertThat(metadata.getChecksum()).isNull();
        }
    }

    @Nested
    @DisplayName("AuditInfo")
    class AuditInfoTests {

        @Test
        @DisplayName("should create audit info")
        void shouldCreateAuditInfo() {
            AuditInfo info = AuditInfo.create("user-123");
            
            assertThat(info.createdBy()).isEqualTo("user-123");
            assertThat(info.createdAt()).isNotNull();
            assertThat(info.createdAt()).isAfter(Instant.parse("2020-01-01T00:00:00Z"));
        }

        @Test
        @DisplayName("should update audit info")
        void shouldUpdateAuditInfo() throws InterruptedException {
            AuditInfo original = AuditInfo.create("user-123");
            Thread.sleep(10); // Ensure different timestamp
            
            AuditInfo updated = original.update();
            
            assertThat(updated.createdBy()).isEqualTo("user-123");
            assertThat(updated.updatedAt()).isAfterOrEqualTo(original.createdAt());
        }
        
        @Test
        @DisplayName("should create system audit info")
        void shouldCreateSystemAuditInfo() {
            AuditInfo info = AuditInfo.system();
            
            assertThat(info.createdBy()).isEqualTo("SYSTEM");
        }
        
        @Test
        @DisplayName("should create anonymous audit info")
        void shouldCreateAnonymousAuditInfo() {
            AuditInfo info = AuditInfo.anonymous();
            
            assertThat(info.createdBy()).isEqualTo("ANONYMOUS");
        }
    }

    @Nested
    @DisplayName("UserPrincipal")
    class UserPrincipalTests {

        @Test
        @DisplayName("should create user principal")
        void shouldCreateUserPrincipal() {
            UserPrincipal user = new UserPrincipal(
                    "user-123",
                    "user@test.com",
                    "Test User",
                    Set.of("USERS", "ADMINS"),
                    Set.of("files.read", "files.write"),
                    null,
                    TEST_ISSUED_AT,
                    TEST_EXPIRES_AT
            );
            
            assertThat(user.userId()).isEqualTo("user-123");
            assertThat(user.email()).isEqualTo("user@test.com");
            assertThat(user.name()).isEqualTo("Test User");
            assertThat(user.groups()).containsExactlyInAnyOrder("USERS", "ADMINS");
            assertThat(user.scopes()).containsExactlyInAnyOrder("files.read", "files.write");
        }

        @Test
        @DisplayName("should check group membership")
        void shouldCheckGroupMembership() {
            UserPrincipal user = new UserPrincipal(
                    "user-123", "user@test.com", "Test User",
                    Set.of("USERS"),
                    Set.of(),
                    null,
                    TEST_ISSUED_AT,
                    TEST_EXPIRES_AT
            );
            
            assertThat(user.hasGroup("USERS")).isTrue();
            assertThat(user.hasGroup("ADMINS")).isFalse();
        }

        @Test
        @DisplayName("should check scope")
        void shouldCheckScope() {
            UserPrincipal user = new UserPrincipal(
                    "user-123", "user@test.com", "Test User",
                    Set.of(),
                    Set.of("files.read"),
                    null,
                    TEST_ISSUED_AT,
                    TEST_EXPIRES_AT
            );
            
            assertThat(user.hasScope("files.read")).isTrue();
            assertThat(user.hasScope("files.write")).isFalse();
        }
    }

    @Nested
    @DisplayName("FileStatus Enum")
    class FileStatusTests {

        @Test
        @DisplayName("should have correct descriptions")
        void shouldHaveCorrectDescriptions() {
            assertThat(FileStatus.PENDING.getDescription()).isNotBlank();
            assertThat(FileStatus.UPLOADED.getDescription()).isNotBlank();
            assertThat(FileStatus.PROCESSING.getDescription()).isNotBlank();
            assertThat(FileStatus.COMPLETED.getDescription()).isNotBlank();
            assertThat(FileStatus.FAILED.getDescription()).isNotBlank();
        }

        @Test
        @DisplayName("should check terminal states")
        void shouldCheckTerminalStates() {
            assertThat(FileStatus.COMPLETED.isTerminal()).isTrue();
            assertThat(FileStatus.FAILED.isTerminal()).isTrue();
            assertThat(FileStatus.PENDING.isTerminal()).isFalse();
            assertThat(FileStatus.PROCESSING.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("should have correct codes")
        void shouldHaveCorrectCodes() {
            assertThat(FileStatus.PENDING.getCode()).isEqualTo("pending");
            assertThat(FileStatus.UPLOADED.getCode()).isEqualTo("uploaded");
            assertThat(FileStatus.SCANNING.getCode()).isEqualTo("scanning");
            assertThat(FileStatus.PROCESSING.getCode()).isEqualTo("processing");
            assertThat(FileStatus.COMPLETED.getCode()).isEqualTo("completed");
            assertThat(FileStatus.FAILED.getCode()).isEqualTo("failed");
        }

        @Test
        @DisplayName("should check canRetry")
        void shouldCheckCanRetry() {
            assertThat(FileStatus.FAILED.canRetry()).isTrue();
            assertThat(FileStatus.COMPLETED.canRetry()).isFalse();
            assertThat(FileStatus.PENDING.canRetry()).isFalse();
        }

        @Test
        @DisplayName("should get status from code")
        void shouldGetStatusFromCode() {
            assertThat(FileStatus.fromCode("pending")).isEqualTo(FileStatus.PENDING);
            assertThat(FileStatus.fromCode("UPLOADED")).isEqualTo(FileStatus.UPLOADED);
            assertThat(FileStatus.fromCode("Processing")).isEqualTo(FileStatus.PROCESSING);
        }

        @Test
        @DisplayName("should throw for unknown code")
        void shouldThrowForUnknownCode() {
            assertThatThrownBy(() -> FileStatus.fromCode("unknown"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown status code");
        }
    }

    @Nested
    @DisplayName("SecureFile Aggregate")
    class SecureFileTests {

        @Test
        @DisplayName("should create pending file")
        void shouldCreatePendingFile() {
            SecureFile file = SecureFile.createPending(
                    FileName.of("test.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(1024),
                    CorrelationId.generate(),
                    "user-123"
            );

            assertThat(file.getId()).isNotNull();
            assertThat(file.getStatus()).isEqualTo(FileStatus.PENDING);
            assertThat(file.getFileName().value()).isEqualTo("test.pdf");
        }

        @Test
        @DisplayName("should transition to uploaded state")
        void shouldTransitionToUploaded() {
            SecureFile pending = SecureFile.createPending(
                    FileName.of("test.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(1024),
                    CorrelationId.generate(),
                    "user-123"
            );

            SecureFile uploaded = pending.markAsUploaded(
                    StorageLocation.of("bucket", "key"),
                    EncryptionMetadata.kmsEncrypted("key-id"),
                    Checksum.sha256("a".repeat(64))
            );

            assertThat(uploaded.getStatus()).isEqualTo(FileStatus.UPLOADED);
            assertThat(uploaded.getStorageLocation()).isNotNull();
        }

        @Test
        @DisplayName("should transition through full lifecycle")
        void shouldTransitionThroughFullLifecycle() {
            SecureFile file = SecureFile.createPending(
                    FileName.of("test.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(1024),
                    CorrelationId.generate(),
                    "user-123"
            );

            file = file.markAsUploaded(
                    StorageLocation.of("bucket", "key"),
                    EncryptionMetadata.kmsEncrypted("key-id"),
                    Checksum.sha256("a".repeat(64))
            );
            assertThat(file.getStatus()).isEqualTo(FileStatus.UPLOADED);

            file = file.markAsProcessing();
            assertThat(file.getStatus()).isEqualTo(FileStatus.PROCESSING);

            file = file.markAsCompleted();
            assertThat(file.getStatus()).isEqualTo(FileStatus.COMPLETED);
        }

        @Test
        @DisplayName("should throw when marking uploaded from wrong state")
        void shouldThrowWhenMarkingUploadedFromWrongState() {
            SecureFile file = SecureFile.createPending(
                    FileName.of("test.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(1024),
                    CorrelationId.generate(),
                    "user-123"
            );
            file = file.markAsUploaded(
                    StorageLocation.of("bucket", "key"),
                    EncryptionMetadata.kmsEncrypted("key-id"),
                    Checksum.sha256("a".repeat(64))
            );

            SecureFile uploadedFile = file;
            assertThatThrownBy(() -> uploadedFile.markAsUploaded(
                    StorageLocation.of("bucket", "key"),
                    EncryptionMetadata.kmsEncrypted("key-id"),
                    Checksum.sha256("a".repeat(64))
            )).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should throw when marking processing from wrong state")
        void shouldThrowWhenMarkingProcessingFromWrongState() {
            SecureFile file = SecureFile.createPending(
                    FileName.of("test.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(1024),
                    CorrelationId.generate(),
                    "user-123"
            );

            assertThatThrownBy(file::markAsProcessing)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should throw when marking completed from wrong state")
        void shouldThrowWhenMarkingCompletedFromWrongState() {
            SecureFile file = SecureFile.createPending(
                    FileName.of("test.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(1024),
                    CorrelationId.generate(),
                    "user-123"
            );

            assertThatThrownBy(file::markAsCompleted)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should allow marking as failed from any state")
        void shouldAllowMarkingAsFailedFromAnyState() {
            SecureFile file = SecureFile.createPending(
                    FileName.of("test.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(1024),
                    CorrelationId.generate(),
                    "user-123"
            );

            SecureFile failed = file.markAsFailed();
            assertThat(failed.getStatus()).isEqualTo(FileStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("EncryptionMetadata Extended Tests")
    class EncryptionMetadataExtendedTests {

        @Test
        @DisplayName("should create with specific algorithm")
        void shouldCreateWithSpecificAlgorithm() {
            EncryptionMetadata metadata = EncryptionMetadata.of(
                    "alias/my-key",
                    EncryptionMetadata.EncryptionAlgorithm.AES_256_GCM
            );

            assertThat(metadata.kmsKeyId()).isEqualTo("alias/my-key");
            assertThat(metadata.algorithm()).isEqualTo(EncryptionMetadata.EncryptionAlgorithm.AES_256_GCM);
        }

        @Test
        @DisplayName("should throw for null algorithm")
        void shouldThrowForNullAlgorithm() {
            assertThatThrownBy(() -> EncryptionMetadata.of("key-id", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should mask KMS key in toString")
        void shouldMaskKmsKeyInToString() {
            EncryptionMetadata metadata = EncryptionMetadata.kmsEncrypted("alias/very-long-key-id-12345");
            
            String str = metadata.toString();
            assertThat(str).contains("alia...2345");
            assertThat(str).doesNotContain("very-long-key-id");
        }

        @Test
        @DisplayName("should mask short KMS key in toString")
        void shouldMaskShortKmsKeyInToString() {
            EncryptionMetadata metadata = EncryptionMetadata.kmsEncrypted("short");
            
            String str = metadata.toString();
            assertThat(str).contains("***");
        }

        @Test
        @DisplayName("should have correct algorithm display name")
        void shouldHaveCorrectAlgorithmDisplayName() {
            assertThat(EncryptionMetadata.EncryptionAlgorithm.AES_256_GCM.getDisplayName())
                    .isEqualTo("AES-256-GCM");
        }
    }

    @Nested
    @DisplayName("FileSize Extended Tests")
    class FileSizeExtendedTests {

        @Test
        @DisplayName("should convert to kilobytes")
        void shouldConvertToKilobytes() {
            FileSize size = FileSize.of(2048);
            assertThat(size.toKilobytes()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("should convert to megabytes")
        void shouldConvertToMegabytes() {
            FileSize size = FileSize.of(10 * 1024 * 1024);
            assertThat(size.toMegabytes()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("should throw for zero size")
        void shouldThrowForZeroSize() {
            assertThatThrownBy(() -> FileSize.of(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least");
        }

        @Test
        @DisplayName("should throw for negative size")
        void shouldThrowForNegativeSize() {
            assertThatThrownBy(() -> FileSize.of(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should create with custom limit")
        void shouldCreateWithCustomLimit() {
            FileSize size = FileSize.ofWithLimit(50 * 1024 * 1024, 200 * 1024 * 1024L);
            assertThat(size.bytes()).isEqualTo(50 * 1024 * 1024);
        }

        @Test
        @DisplayName("should throw when exceeding custom limit")
        void shouldThrowWhenExceedingCustomLimit() {
            assertThatThrownBy(() -> FileSize.ofWithLimit(100, 50))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds limit");
        }

        @Test
        @DisplayName("toString should return human readable")
        void toStringShouldReturnHumanReadable() {
            FileSize size = FileSize.of(1024);
            assertThat(size.toString()).isEqualTo("1.00 KB");
        }
    }

    @Nested
    @DisplayName("Checksum Extended Tests")
    class ChecksumExtendedTests {

        @Test
        @DisplayName("should throw for non-hex checksum")
        void shouldThrowForNonHexChecksum() {
            assertThatThrownBy(() -> Checksum.sha256("g".repeat(64)))  // 'g' is not hex
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should have correct algorithm display name")
        void shouldHaveCorrectAlgorithmDisplayName() {
            assertThat(Checksum.Algorithm.SHA256.getDisplayName()).isEqualTo("SHA-256");
        }

        @Test
        @DisplayName("should match equal checksums")
        void shouldMatchEqualChecksums() {
            Checksum checksum1 = Checksum.sha256("a".repeat(64));
            Checksum checksum2 = Checksum.sha256("A".repeat(64));
            
            assertThat(checksum1.matches(checksum2)).isTrue();
        }

        @Test
        @DisplayName("should not match different checksums")
        void shouldNotMatchDifferentChecksums() {
            Checksum checksum1 = Checksum.sha256("a".repeat(64));
            Checksum checksum2 = Checksum.sha256("b".repeat(64));
            
            assertThat(checksum1.matches(checksum2)).isFalse();
        }

        @Test
        @DisplayName("should not match null checksum")
        void shouldNotMatchNullChecksum() {
            Checksum checksum1 = Checksum.sha256("a".repeat(64));
            assertThat(checksum1.matches(null)).isFalse();
        }

        @Test
        @DisplayName("toString should contain algorithm and value")
        void toStringShouldContainAlgorithmAndValue() {
            Checksum checksum = Checksum.sha256("a".repeat(64));
            assertThat(checksum.toString()).contains("SHA-256");
            assertThat(checksum.toString()).contains("a".repeat(64));
        }
    }

    @Nested
    @DisplayName("UserPrincipal Extended Tests")
    class UserPrincipalExtendedTests {

        @Test
        @DisplayName("should check isAdmin")
        void shouldCheckIsAdmin() {
            UserPrincipal admin = new UserPrincipal(
                    "admin-1", "admin@test.com", "Admin",
                    Set.of("admins"),
                    Set.of(),
                    null,
                    TEST_ISSUED_AT,
                    TEST_EXPIRES_AT
            );
            assertThat(admin.isAdmin()).isTrue();

            UserPrincipal user = new UserPrincipal(
                    "user-1", "user@test.com", "User",
                    Set.of("users"),
                    Set.of(),
                    null,
                    TEST_ISSUED_AT,
                    TEST_EXPIRES_AT
            );
            assertThat(user.isAdmin()).isFalse();
        }

        @Test
        @DisplayName("should check isTokenExpired")
        void shouldCheckIsTokenExpired() {
            UserPrincipal expiredUser = new UserPrincipal(
                    "user-1", "user@test.com", "User",
                    Set.of(),
                    Set.of(),
                    null,
                    Instant.parse("2020-01-15T10:00:00Z"),
                    Instant.parse("2020-01-15T11:00:00Z")
            );
            assertThat(expiredUser.isTokenExpired()).isTrue();

            UserPrincipal validUser = new UserPrincipal(
                    "user-2", "user2@test.com", "User2",
                    Set.of(),
                    Set.of(),
                    null,
                    Instant.parse("2999-01-15T10:00:00Z"),
                    Instant.parse("2999-01-15T11:00:00Z")
            );
            assertThat(validUser.isTokenExpired()).isFalse();
        }

        @Test
        @DisplayName("should build with UserPrincipal.builder()")
        void shouldBuildWithBuilder() {
            UserPrincipal user = UserPrincipal.builder()
                    .userId("user-123")
                    .email("user@test.com")
                    .name("Test User")
                    .groups(Set.of("users"))
                    .scopes(Set.of("files.read"))
                    .tenantId("tenant-1")
                    .tokenIssuedAt(TEST_ISSUED_AT)
                    .tokenExpiresAt(TEST_EXPIRES_AT)
                    .build();

            assertThat(user.userId()).isEqualTo("user-123");
            assertThat(user.email()).isEqualTo("user@test.com");
            assertThat(user.tenantId()).isEqualTo("tenant-1");
        }
    }

    @Nested
    @DisplayName("StorageLocation Extended Tests")
    class StorageLocationExtendedTests {

        @Test
        @DisplayName("should return S3 URI")
        void shouldReturnS3Uri() {
            StorageLocation location = StorageLocation.of("my-bucket", "path/to/file.pdf");
            assertThat(location.toS3Uri()).isEqualTo("s3://my-bucket/path/to/file.pdf");
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            StorageLocation location = StorageLocation.of("bucket", "key");
            assertThat(location.toString()).contains("bucket").contains("key");
        }
    }
}
