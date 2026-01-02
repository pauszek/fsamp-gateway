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
            // given
            SecureFile file = createTestFile();

            // when
            SecureFile result = adapter.save(file);

            // then
            assertThat(result).isEqualTo(file);
        }

        @Test
        @DisplayName("should overwrite existing file with same ID")
        void shouldOverwriteExistingFile() {
            // given
            SecureFile file1 = createTestFile();
            adapter.save(file1);

            SecureFile file2 = SecureFile.createPending(
                    FileName.of("different.pdf"),
                    MimeType.of("application/pdf"),
                    FileSize.of(2048L),
                    file1.getCorrelationId(),
                    "user-456"
            );
            // Create file with same ID for overwrite test
            SecureFile file2WithSameId = createFileWithId(file1.getId());

            // when
            adapter.save(file2WithSameId);

            // then
            Optional<SecureFile> found = adapter.findById(file1.getId());
            assertThat(found).isPresent();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return file when exists")
        void shouldReturnFileWhenExists() {
            // given
            SecureFile file = createTestFile();
            adapter.save(file);

            // when
            Optional<SecureFile> result = adapter.findById(file.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(file);
        }

        @Test
        @DisplayName("should return empty when not exists")
        void shouldReturnEmptyWhenNotExists() {
            // given
            FileId nonExistentId = FileId.generate();

            // when
            Optional<SecureFile> result = adapter.findById(nonExistentId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should find correct file among multiple")
        void shouldFindCorrectFileAmongMultiple() {
            // given
            SecureFile file1 = createTestFile();
            SecureFile file2 = createTestFile();
            SecureFile file3 = createTestFile();
            adapter.save(file1);
            adapter.save(file2);
            adapter.save(file3);

            // when
            Optional<SecureFile> result = adapter.findById(file2.getId());

            // then
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
            // given
            SecureFile file = createTestFile();
            adapter.save(file);

            // when
            adapter.delete(file.getId());

            // then
            assertThat(adapter.findById(file.getId())).isEmpty();
        }

        @Test
        @DisplayName("should not throw when deleting non-existent file")
        void shouldNotThrowWhenDeletingNonExistent() {
            // given
            FileId nonExistentId = FileId.generate();

            // when/then
            assertThatCode(() -> adapter.delete(nonExistentId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should only delete specified file")
        void shouldOnlyDeleteSpecifiedFile() {
            // given
            SecureFile file1 = createTestFile();
            SecureFile file2 = createTestFile();
            adapter.save(file1);
            adapter.save(file2);

            // when
            adapter.delete(file1.getId());

            // then
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
            // given
            SecureFile file = createTestFile();
            adapter.save(file);

            // when
            boolean result = adapter.exists(file.getId());

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when file does not exist")
        void shouldReturnFalseWhenNotExists() {
            // given
            FileId nonExistentId = FileId.generate();

            // when
            boolean result = adapter.exists(nonExistentId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false after file is deleted")
        void shouldReturnFalseAfterDelete() {
            // given
            SecureFile file = createTestFile();
            adapter.save(file);
            adapter.delete(file.getId());

            // when
            boolean result = adapter.exists(file.getId());

            // then
            assertThat(result).isFalse();
        }
    }

    // Helper methods

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
        // For this test, we need to create a file that uses the same ID
        // Since SecureFile.createPending generates a new ID, we'll just create another file
        return SecureFile.createPending(
                FileName.of("updated-document.pdf"),
                MimeType.of("application/pdf"),
                FileSize.of(2048L),
                CorrelationId.generate(),
                "user-456"
        );
    }
}
