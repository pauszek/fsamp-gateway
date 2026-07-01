package io.github.pauszek.fsampgateway.adapter.out.persistence;

import io.github.pauszek.fsampgateway.domain.model.*;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("InMemoryFileRepositoryAdapter")
class InMemoryFileRepositoryAdapterTest {

    private InMemoryFileRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InMemoryFileRepositoryAdapter();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("should save file and return it")
        void shouldSaveFileAndReturnIt() {
            SecureFile file = createTestFile();

            SecureFile result = adapter.save(file);

            assertThat(result).isEqualTo(file);
        }

        @Test
        @DisplayName("should overwrite existing file with same ID")
        void shouldOverwriteExistingFile() {
            SecureFile file1 = createTestFile();
            adapter.save(file1);

            SecureFile file2WithSameId = createFileWithId(file1.getId());

            adapter.save(file2WithSameId);

            Optional<SecureFile> found = adapter.findById(file1.getId());
            assertThat(found).contains(file2WithSameId);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return file when exists")
        void shouldReturnFileWhenExists() {
            SecureFile file = createTestFile();
            adapter.save(file);

            Optional<SecureFile> result = adapter.findById(file.getId());

            assertThat(result).contains(file);
        }

        @Test
        @DisplayName("should return empty when not exists")
        void shouldReturnEmptyWhenNotExists() {
            FileId nonExistentId = FileId.generate();

            Optional<SecureFile> result = adapter.findById(nonExistentId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should find correct file among multiple")
        void shouldFindCorrectFileAmongMultiple() {
            SecureFile file1 = createTestFile();
            SecureFile file2 = createTestFile();
            SecureFile file3 = createTestFile();
            adapter.save(file1);
            adapter.save(file2);
            adapter.save(file3);

            Optional<SecureFile> result = adapter.findById(file2.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(file2.getId());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should delete existing file")
        void shouldDeleteExistingFile() {
            SecureFile file = createTestFile();
            adapter.save(file);

            adapter.delete(file.getId());

            assertThat(adapter.findById(file.getId())).isEmpty();
        }

        @Test
        @DisplayName("should not throw when deleting non-existent file")
        void shouldNotThrowWhenDeletingNonExistent() {
            FileId nonExistentId = FileId.generate();

            assertThatCode(() -> adapter.delete(nonExistentId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should only delete specified file")
        void shouldOnlyDeleteSpecifiedFile() {
            SecureFile file1 = createTestFile();
            SecureFile file2 = createTestFile();
            adapter.save(file1);
            adapter.save(file2);

            adapter.delete(file1.getId());

            assertThat(adapter.findById(file1.getId())).isEmpty();
            assertThat(adapter.findById(file2.getId())).isPresent();
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("should return true when file exists")
        void shouldReturnTrueWhenExists() {
            SecureFile file = createTestFile();
            adapter.save(file);

            boolean result = adapter.exists(file.getId());

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when file does not exist")
        void shouldReturnFalseWhenNotExists() {
            FileId nonExistentId = FileId.generate();

            boolean result = adapter.exists(nonExistentId);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false after file is deleted")
        void shouldReturnFalseAfterDelete() {
            SecureFile file = createTestFile();
            adapter.save(file);
            adapter.delete(file.getId());

            boolean result = adapter.exists(file.getId());

            assertThat(result).isFalse();
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

    private SecureFile createFileWithId(FileId id) {
        return SecureFile.builder()
                .id(id)
                .correlationId(CorrelationId.generate())
                .fileName(FileName.of("updated-document.pdf"))
                .mimeType(MimeType.of("application/pdf"))
                .size(FileSize.of(2048L))
                .status(FileStatus.PENDING)
                .auditInfo(AuditInfo.create("user-456"))
                .build();
    }
}
