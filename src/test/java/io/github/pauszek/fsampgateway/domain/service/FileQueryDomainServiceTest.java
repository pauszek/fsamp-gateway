package io.github.pauszek.fsampgateway.domain.service;

import io.github.pauszek.fsampgateway.domain.exception.FileNotFoundException;
import io.github.pauszek.fsampgateway.domain.model.*;
import io.github.pauszek.fsampgateway.domain.port.out.FileRepositoryPort;
import io.github.pauszek.fsampgateway.domain.port.out.FileStoragePort;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link FileQueryDomainService}.
 * 
 * Tests get and delete use cases with mocked ports.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileQueryDomainService")
class FileQueryDomainServiceTest {

    @Mock
    private FileRepositoryPort fileRepository;

    @Mock
    private FileStoragePort fileStorage;

    @Captor
    private ArgumentCaptor<SecureFile> fileCaptor;

    private FileQueryDomainService service;

    @BeforeEach
    void setUp() {
        service = new FileQueryDomainService(fileRepository, fileStorage);
    }

    // ========================================================================
    // getById()
    // ========================================================================

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("should return file when found")
        void shouldReturnFileWhenFound() {
            // given
            SecureFile file = createTestFile();
            given(fileRepository.findById(file.getId())).willReturn(Optional.of(file));

            // when
            Optional<SecureFile> result = service.getById(file.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(file);
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            // given
            FileId fileId = FileId.generate();
            given(fileRepository.findById(fileId)).willReturn(Optional.empty());

            // when
            Optional<SecureFile> result = service.getById(fileId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            // given
            FileId fileId = FileId.generate();
            given(fileRepository.findById(fileId)).willReturn(Optional.empty());

            // when
            service.getById(fileId);

            // then
            then(fileRepository).should().findById(fileId);
            then(fileStorage).shouldHaveNoInteractions();
        }
    }

    // ========================================================================
    // getByIdOrThrow()
    // ========================================================================

    @Nested
    @DisplayName("getByIdOrThrow")
    class GetByIdOrThrow {

        @Test
        @DisplayName("should return file when found")
        void shouldReturnFileWhenFound() {
            // given
            SecureFile file = createTestFile();
            given(fileRepository.findById(file.getId())).willReturn(Optional.of(file));

            // when
            SecureFile result = service.getByIdOrThrow(file.getId());

            // then
            assertThat(result).isEqualTo(file);
        }

        @Test
        @DisplayName("should throw FileNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            // given
            FileId fileId = FileId.generate();
            given(fileRepository.findById(fileId)).willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> service.getByIdOrThrow(fileId))
                    .isInstanceOf(FileNotFoundException.class)
                    .hasMessageContaining(fileId.toString());
        }
    }

    // ========================================================================
    // execute() (delete)
    // ========================================================================

    @Nested
    @DisplayName("execute (delete)")
    class Execute {

        @Test
        @DisplayName("should delete uploaded file: mark failed → delete S3 → delete metadata")
        void shouldDeleteUploadedFile() {
            // given
            SecureFile uploadedFile = createUploadedFile();
            given(fileRepository.findById(uploadedFile.getId())).willReturn(Optional.of(uploadedFile));
            given(fileRepository.save(any(SecureFile.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            service.execute(uploadedFile.getId());

            // then - verify workflow order
            var inOrder = inOrder(fileRepository, fileStorage);

            // 1. Find file
            inOrder.verify(fileRepository).findById(uploadedFile.getId());

            // 2. Save with FAILED status
            inOrder.verify(fileRepository).save(fileCaptor.capture());
            assertThat(fileCaptor.getValue().getStatus()).isEqualTo(FileStatus.FAILED);

            // 3. Delete from S3
            inOrder.verify(fileStorage).delete(uploadedFile.getStorageLocation());

            // 4. Delete metadata
            inOrder.verify(fileRepository).delete(uploadedFile.getId());
        }

        @Test
        @DisplayName("should delete pending file without S3 call")
        void shouldDeletePendingFileWithoutS3Call() {
            // given
            SecureFile pendingFile = createTestFile(); // pending, no storage location
            given(fileRepository.findById(pendingFile.getId())).willReturn(Optional.of(pendingFile));
            given(fileRepository.save(any(SecureFile.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            service.execute(pendingFile.getId());

            // then
            then(fileStorage).shouldHaveNoInteractions(); // no S3 delete for pending file
            then(fileRepository).should().save(fileCaptor.capture());
            assertThat(fileCaptor.getValue().getStatus()).isEqualTo(FileStatus.FAILED);
            then(fileRepository).should().delete(pendingFile.getId());
        }

        @Test
        @DisplayName("should throw FileNotFoundException when file does not exist")
        void shouldThrowWhenFileNotFound() {
            // given
            FileId fileId = FileId.generate();
            given(fileRepository.findById(fileId)).willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> service.execute(fileId))
                    .isInstanceOf(FileNotFoundException.class);

            then(fileRepository).should(never()).save(any());
            then(fileRepository).should(never()).delete(any());
            then(fileStorage).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should continue deletion when S3 delete fails")
        void shouldContinueWhenS3DeleteFails() {
            // given
            SecureFile uploadedFile = createUploadedFile();
            given(fileRepository.findById(uploadedFile.getId())).willReturn(Optional.of(uploadedFile));
            given(fileRepository.save(any(SecureFile.class))).willAnswer(inv -> inv.getArgument(0));
            willThrow(new RuntimeException("S3 unavailable"))
                    .given(fileStorage).delete(any(StorageLocation.class));

            // when - should not throw
            assertThatCode(() -> service.execute(uploadedFile.getId()))
                    .doesNotThrowAnyException();

            // then - metadata still deleted despite S3 failure
            then(fileRepository).should().delete(uploadedFile.getId());
        }
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    private SecureFile createTestFile() {
        return SecureFile.createPending(
                FileName.of("test-document.pdf"),
                MimeType.of("application/pdf"),
                FileSize.of(1024L),
                CorrelationId.generate(),
                "user-123"
        );
    }

    private SecureFile createUploadedFile() {
        SecureFile pending = createTestFile();
        return pending.markAsUploaded(
                StorageLocation.of("test-bucket", "test-key"),
                new EncryptionMetadata("alias/test-kms-key", EncryptionMetadata.EncryptionAlgorithm.AES_256_GCM, true),
                Checksum.sha256("a".repeat(64))
        );
    }
}
