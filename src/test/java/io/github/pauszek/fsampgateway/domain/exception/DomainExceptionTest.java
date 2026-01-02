package io.github.pauszek.fsampgateway.domain.exception;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Domain Exceptions")
class DomainExceptionTest {

    @Nested
    @DisplayName("FileValidationException")
    class FileValidationExceptionTest {

        @Test
        @DisplayName("should preserve message")
        void shouldPreserveMessage() {
            var ex = new FileValidationException("Invalid file format");
            assertThat(ex.getMessage()).isEqualTo("Invalid file format");
        }

        @Test
        @DisplayName("should preserve cause")
        void shouldPreserveCause() {
            var cause = new RuntimeException("Root cause");
            var ex = new FileValidationException("Validation failed", cause);
            assertThat(ex.getCause()).isEqualTo(cause);
        }

        @Test
        @DisplayName("should return correct errorCode")
        void shouldReturnCorrectErrorCode() {
            var ex = new FileValidationException("Invalid");
            assertThat(ex.getErrorCode()).isEqualTo("FILE_VALIDATION_ERROR");
        }
    }

    @Nested
    @DisplayName("FileNotFoundException")
    class FileNotFoundExceptionTest {

        @Test
        @DisplayName("should preserve message")
        void shouldPreserveMessage() {
            var ex = new FileNotFoundException("File not found");
            assertThat(ex.getMessage()).isEqualTo("File not found");
        }

        @Test
        @DisplayName("should return correct errorCode")
        void shouldReturnCorrectErrorCode() {
            var ex = new FileNotFoundException("Not found");
            assertThat(ex.getErrorCode()).isEqualTo("FILE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("StorageException")
    class StorageExceptionTest {

        @Test
        @DisplayName("should preserve message")
        void shouldPreserveMessage() {
            var ex = new StorageException("S3 error");
            assertThat(ex.getMessage()).isEqualTo("S3 error");
        }

        @Test
        @DisplayName("should preserve cause")
        void shouldPreserveCause() {
            var cause = new RuntimeException("Connection failed");
            var ex = new StorageException("Storage failed", cause);
            assertThat(ex.getCause()).isEqualTo(cause);
        }

        @Test
        @DisplayName("should return correct errorCode")
        void shouldReturnCorrectErrorCode() {
            var ex = new StorageException("Error");
            assertThat(ex.getErrorCode()).isEqualTo("STORAGE_ERROR");
        }
    }

    @Nested
    @DisplayName("EventPublishException")
    class EventPublishExceptionTest {

        @Test
        @DisplayName("should preserve message")
        void shouldPreserveMessage() {
            var ex = new EventPublishException("SNS error");
            assertThat(ex.getMessage()).isEqualTo("SNS error");
        }

        @Test
        @DisplayName("should preserve cause")
        void shouldPreserveCause() {
            var cause = new RuntimeException("Publish failed");
            var ex = new EventPublishException("Event failed", cause);
            assertThat(ex.getCause()).isEqualTo(cause);
        }

        @Test
        @DisplayName("should return correct errorCode")
        void shouldReturnCorrectErrorCode() {
            var ex = new EventPublishException("Error");
            assertThat(ex.getErrorCode()).isEqualTo("EVENT_PUBLISH_ERROR");
        }
    }

    @Nested
    @DisplayName("DomainException hierarchy")
    class DomainExceptionHierarchy {

        @Test
        @DisplayName("all domain exceptions should extend DomainException")
        void allDomainExceptionsShouldExtendDomainException() {
            assertThat(new FileValidationException("test")).isInstanceOf(DomainException.class);
            assertThat(new FileNotFoundException("test")).isInstanceOf(DomainException.class);
            assertThat(new StorageException("test")).isInstanceOf(DomainException.class);
            assertThat(new EventPublishException("test")).isInstanceOf(DomainException.class);
        }
    }
}
