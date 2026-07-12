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
    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("should return file when found")
        void shouldReturnFileWhenFound() {
            SecureFile file = createTestFile();
            given(fileRepository.findById(file.getId())).willReturn(Optional.of(file));

            Optional<SecureFile> result = service.getById(file.getId());

            assertThat(result).contains(file);
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            FileId fileId = FileId.generate();
            given(fileRepository.findById(fileId)).willReturn(Optional.empty());

            Optional<SecureFile> result = service.getById(fileId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            FileId fileId = FileId.generate();
            given(fileRepository.findById(fileId)).willReturn(Optional.empty());

            service.getById(fileId);

            then(fileRepository).should().findById(fileId);
            then(fileStorage).shouldHaveNoInteractions();
        }
    }
    @Nested
    @DisplayName("getByIdOrThrow")
    class GetByIdOrThrow {

        @Test
        @DisplayName("should return file when found")
        void shouldReturnFileWhenFound() {
            SecureFile file = createTestFile();
            given(fileRepository.findById(file.getId())).willReturn(Optional.of(file));

            SecureFile result = service.getByIdOrThrow(file.getId());

            assertThat(result).isEqualTo(file);
        }

        @Test
        @DisplayName("should throw FileNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            FileId fileId = FileId.generate();
            given(fileRepository.findById(fileId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByIdOrThrow(fileId))
                    .isInstanceOf(FileNotFoundException.class)
                    .hasMessageContaining(fileId.toString());
        }
    }
    @Nested
    @DisplayName("execute (delete)")
    class Execute {

        @Test
        @DisplayName("should delete uploaded file: mark deleting -> delete S3 -> delete metadata")
        void shouldDeleteUploadedFile() {
            SecureFile uploadedFile = createUploadedFile();
            given(fileRepository.findById(uploadedFile.getId())).willReturn(Optional.of(uploadedFile));
            given(fileRepository.save(any(SecureFile.class))).willAnswer(inv -> inv.getArgument(0));

            service.execute(uploadedFile.getId());

            var inOrder = inOrder(fileRepository, fileStorage);
            inOrder.verify(fileRepository).findById(uploadedFile.getId());
            inOrder.verify(fileRepository).save(fileCaptor.capture());
            assertThat(fileCaptor.getValue().getStatus()).isEqualTo(FileStatus.DELETING);
            inOrder.verify(fileStorage).delete(uploadedFile.getStorageLocation());
            inOrder.verify(fileRepository).delete(uploadedFile.getId());
        }

        @Test
        @DisplayName("should delete pending file without S3 call")
        void shouldDeletePendingFileWithoutS3Call() {
            SecureFile pendingFile = createTestFile(); // pending, no storage location
            given(fileRepository.findById(pendingFile.getId())).willReturn(Optional.of(pendingFile));
            given(fileRepository.save(any(SecureFile.class))).willAnswer(inv -> inv.getArgument(0));

            service.execute(pendingFile.getId());

            then(fileStorage).shouldHaveNoInteractions(); // no S3 delete for pending file
            then(fileRepository).should().save(fileCaptor.capture());
            assertThat(fileCaptor.getValue().getStatus()).isEqualTo(FileStatus.DELETING);
            then(fileRepository).should().delete(pendingFile.getId());
        }

        @Test
        @DisplayName("should throw FileNotFoundException when file does not exist")
        void shouldThrowWhenFileNotFound() {
            FileId fileId = FileId.generate();
            given(fileRepository.findById(fileId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.execute(fileId))
                    .isInstanceOf(FileNotFoundException.class);

            then(fileRepository).should(never()).save(any());
            then(fileRepository).should(never()).delete(any());
            then(fileStorage).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should keep metadata recoverable when S3 delete fails")
        void shouldKeepMetadataWhenS3DeleteFails() {
            SecureFile uploadedFile = createUploadedFile();
            given(fileRepository.findById(uploadedFile.getId())).willReturn(Optional.of(uploadedFile));
            given(fileRepository.save(any(SecureFile.class))).willAnswer(inv -> inv.getArgument(0));
            willThrow(new RuntimeException("S3 unavailable"))
                    .given(fileStorage).delete(any(StorageLocation.class));

            assertThatThrownBy(() -> service.execute(uploadedFile.getId()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("S3 unavailable");

            then(fileRepository).should(never()).delete(uploadedFile.getId());
        }
    }
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
