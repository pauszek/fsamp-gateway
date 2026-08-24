package io.github.pauszek.fsampgateway.domain.service;

import io.github.pauszek.fsampgateway.domain.command.UploadFileCommand;
import io.github.pauszek.fsampgateway.domain.event.FileUploadedEvent;
import io.github.pauszek.fsampgateway.domain.exception.FileValidationException;
import io.github.pauszek.fsampgateway.domain.model.*;
import io.github.pauszek.fsampgateway.domain.port.out.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileUploadDomainService")
class FileUploadDomainServiceTest {

    @Mock
    private ContentValidatorPort contentValidator;

    @Mock
    private FileStoragePort fileStorage;

    @Mock
    private EventPublisherPort eventPublisher;

    @Mock
    private FileRepositoryPort fileRepository;

    private FileUploadDomainService service;

    @Captor
    private ArgumentCaptor<FileUploadedEvent> eventCaptor;

    @Captor
    private ArgumentCaptor<SecureFile> fileCaptor;

    private static final String TEST_FILENAME = "test-document.pdf";
    private static final String TEST_CONTENT_TYPE = "application/pdf";
    private static final byte[] TEST_CONTENT = "PDF content".getBytes();
    private static final String TEST_USER = "user-123";
    private static final String TEST_CORRELATION_ID = "a1b2c3d4-e5f6-4890-a1b2-c3d4e5f67890";
    private static final String TEST_MESSAGE_ID = "msg-123";

    @BeforeEach
    void setUp() {
        service = new FileUploadDomainService(
                contentValidator,
                fileStorage,
                eventPublisher,
                fileRepository,
                MimeType.ALLOWED_TYPES,
                FileSize.MAX_SIZE,
                false,
                null
        );
    }

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Nested
    @DisplayName("execute - successful upload")
    class SuccessfulUpload {

        @Test
        @DisplayName("should complete full upload workflow")
        void shouldCompleteFullUploadWorkflow() {
            var command = createValidCommand();
            mockSuccessfulValidation();
            mockSuccessfulStorage();
            mockSuccessfulRepository();
            mockSuccessfulEventPublish();

            SecureFile result = service.execute(command);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(FileStatus.UPLOADED);
            assertThat(result.getFileName().value()).isEqualTo(TEST_FILENAME);
            assertThat(result.getMimeType().value()).isEqualTo(TEST_CONTENT_TYPE);
            assertThat(result.getCorrelationId().value()).isEqualTo(TEST_CORRELATION_ID);

            var inOrder = inOrder(contentValidator, fileStorage, fileRepository, eventPublisher);
            inOrder.verify(contentValidator).validate(any(), any(), anyString());
            inOrder.verify(contentValidator).computeChecksum(any());
            inOrder.verify(fileStorage).store(any(), any(), any(), any(), any());
            inOrder.verify(fileRepository).save(any());
            inOrder.verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("should generate correlationId when not provided")
        void shouldGenerateCorrelationIdWhenNotProvided() {
            var command = UploadFileCommand.builder()
                    .fileName(TEST_FILENAME)
                    .contentType(TEST_CONTENT_TYPE)
                    .size(TEST_CONTENT.length)
                    .content(new ByteArrayInputStream(TEST_CONTENT))
                    .uploadedBy(TEST_USER)
                    .build();
            mockSuccessfulValidation();
            mockSuccessfulStorage();
            mockSuccessfulRepository();
            mockSuccessfulEventPublish();

            SecureFile result = service.execute(command);

            assertThat(result.getCorrelationId().value()).isNotNull().isNotBlank();
        }

        @Test
        void shouldUseReservedFileIdForIdempotentUpload() {
            FileId reservedFileId = FileId.generate();
            var command = UploadFileCommand.builder()
                    .fileName(TEST_FILENAME)
                    .contentType(TEST_CONTENT_TYPE)
                    .size(TEST_CONTENT.length)
                    .content(new ByteArrayInputStream(TEST_CONTENT))
                    .correlationId(TEST_CORRELATION_ID)
                    .uploadedBy(TEST_USER)
                    .fileId(reservedFileId)
                    .build();
            mockSuccessfulValidation();
            mockSuccessfulStorage();
            mockSuccessfulRepository();
            mockSuccessfulEventPublish();

            SecureFile result = service.execute(command);

            assertThat(result.getId()).isEqualTo(reservedFileId);
            then(fileStorage).should().store(eq(reservedFileId), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should compute and include checksum")
        void shouldComputeAndIncludeChecksum() {
            var command = createValidCommand();
            mockSuccessfulValidation();
            mockSuccessfulStorage();
            mockSuccessfulRepository();
            mockSuccessfulEventPublish();

            SecureFile result = service.execute(command);

            assertThat(result.getChecksum()).isNotNull();
            assertThat(result.getChecksum().value()).hasSize(64); // SHA-256 hex
        }

        @Test
        @DisplayName("should publish FileUploadedEvent with correct data")
        void shouldPublishFileUploadedEvent() {
            service = new FileUploadDomainService(
                    contentValidator,
                    fileStorage,
                    eventPublisher,
                    fileRepository,
                    MimeType.ALLOWED_TYPES,
                    FileSize.MAX_SIZE,
                    false,
                    "us-west-2"
            );
            var command = createValidCommand();
            mockSuccessfulValidation();
            mockSuccessfulStorage();
            mockSuccessfulRepository();
            mockSuccessfulEventPublish();

            service.execute(command);

            then(eventPublisher).should().publish(eventCaptor.capture());
            FileUploadedEvent event = eventCaptor.getValue();
            assertThat(event.fileId()).isNotNull();
            assertThat(event.eventId())
                    .as("one FILE_UPLOADED event identity must be stable across retries")
                    .isEqualTo(event.fileId());
            assertThat(event.fileMetadata().getOriginalFilename()).isEqualTo(TEST_FILENAME);
            assertThat(event.fileMetadata().getMimeType()).isEqualTo(TEST_CONTENT_TYPE);
            assertThat(event.storageLocation().getRegion()).isEqualTo("us-west-2");
        }

        @Test
        @DisplayName("should use transactional outbox instead of direct SNS publish when repository supports it")
        void shouldUseTransactionalOutboxWhenAvailable() {
            var command = createValidCommand();
            mockSuccessfulValidation();
            mockSuccessfulStorage();
            given(fileRepository.supportsTransactionalOutbox()).willReturn(true);
            given(fileRepository.saveWithOutbox(any(), any())).willAnswer(invocation -> invocation.getArgument(0));

            SecureFile result = service.execute(command);

            assertThat(result.getStatus()).isEqualTo(FileStatus.UPLOADED);
            then(fileRepository).should().saveWithOutbox(fileCaptor.capture(), eventCaptor.capture());
            assertThat(eventCaptor.getValue().fileId()).isEqualTo(fileCaptor.getValue().getId().value());
            then(eventPublisher).shouldHaveNoInteractions();
            then(fileRepository).should(never()).save(any());
        }

        @Test
        void shouldRemoveASecondObjectAfterRecoveringAnAlreadyCommittedUpload() {
            StorageLocation attemptedLocation = StorageLocation.of("bucket", "key");
            StorageLocation committedLocation = StorageLocation.of("bucket", "previous-day/key");
            mockSuccessfulValidation();
            mockSuccessfulStorage();
            given(fileRepository.supportsTransactionalOutbox()).willReturn(true);
            given(fileRepository.saveWithOutbox(any(), any())).willAnswer(invocation -> {
                SecureFile attempted = invocation.getArgument(0);
                return attempted.toBuilder().storageLocation(committedLocation).build();
            });

            SecureFile result = service.execute(createValidCommand());

            assertThat(result.getStorageLocation()).isEqualTo(committedLocation);
            then(fileStorage).should().delete(attemptedLocation);
            then(eventPublisher).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should allow direct SNS publish after outbox write only for local fallback")
        void shouldAllowLocalDirectPublishAfterOutboxWrite() {
            var localFallbackService = new FileUploadDomainService(
                    contentValidator,
                    fileStorage,
                    eventPublisher,
                    fileRepository,
                    MimeType.ALLOWED_TYPES,
                    FileSize.MAX_SIZE,
                    true,
                    null
            );
            var command = createValidCommand();
            mockSuccessfulValidation();
            mockSuccessfulStorage();
            given(fileRepository.supportsTransactionalOutbox()).willReturn(true);
            given(fileRepository.saveWithOutbox(any(), any())).willAnswer(invocation -> invocation.getArgument(0));
            mockSuccessfulEventPublish();

            localFallbackService.execute(command);

            then(fileRepository).should().saveWithOutbox(any(), eventCaptor.capture());
            then(eventPublisher).should().publish(eventCaptor.getValue());
        }

        @Test
        void shouldRelyOnDurableOutboxWhenLocalDirectPublishFails() {
            var localFallbackService = new FileUploadDomainService(
                    contentValidator,
                    fileStorage,
                    eventPublisher,
                    fileRepository,
                    MimeType.ALLOWED_TYPES,
                    FileSize.MAX_SIZE,
                    true,
                    null
            );
            mockSuccessfulValidation();
            mockSuccessfulStorage();
            given(fileRepository.supportsTransactionalOutbox()).willReturn(true);
            given(fileRepository.saveWithOutbox(any(), any()))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(eventPublisher.publish(any())).willThrow(new RuntimeException("SNS unavailable"));

            SecureFile result = localFallbackService.execute(createValidCommand());

            assertThat(result.getStatus()).isEqualTo(FileStatus.UPLOADED);
            then(fileRepository).should(never()).delete(any());
            then(fileStorage).should(never()).delete(any());
        }

        @Test
        @DisplayName("should set MDC correlationId during processing")
        void shouldSetMdcCorrelationIdDuringProcessing() {
            var command = createValidCommand();
            mockSuccessfulValidation();
            mockSuccessfulStorage();
            mockSuccessfulEventPublish();

            given(fileRepository.save(any())).willAnswer(invocation -> {
                assertThat(MDC.get("correlationId")).isEqualTo(TEST_CORRELATION_ID);
                return invocation.getArgument(0);
            });

            service.execute(command);

            assertThat(MDC.get("correlationId")).isNull();
        }

        @Test
        void shouldRestorePreviousMdcCorrelationId() {
            MDC.put("correlationId", "previous-correlation-id");
            mockSuccessfulValidation();
            mockSuccessfulStorage();
            mockSuccessfulRepository();
            mockSuccessfulEventPublish();

            service.execute(createValidCommand());

            assertThat(MDC.get("correlationId")).isEqualTo("previous-correlation-id");
        }
    }

    @Nested
    @DisplayName("execute - validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should throw FileValidationException for invalid content")
        void shouldThrowForInvalidContent() {
            var command = createValidCommand();
            given(contentValidator.validate(any(), any(), anyString()))
                    .willReturn(ValidationResult.invalid(
                            MimeType.of("application/octet-stream"),
                            "Content does not match declared type"
                    ));

            assertThatThrownBy(() -> service.execute(command))
                    .isInstanceOf(FileValidationException.class)
                    .hasMessageContaining("Content does not match declared type");

            then(fileStorage).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should throw FileValidationException for disallowed MIME type")
        void shouldThrowForDisallowedMimeType() {
            var command = createValidCommand();
            given(contentValidator.validate(any(), any(), anyString()))
                    .willReturn(ValidationResult.valid(MimeType.of("application/x-executable")));

            assertThatThrownBy(() -> service.execute(command))
                    .isInstanceOf(FileValidationException.class)
                    .hasMessageContaining("not allowed");

            then(fileStorage).shouldHaveNoInteractions();
        }

        @Test
        void shouldRejectMissingContentType() {
            var command = UploadFileCommand.builder()
                    .fileName(TEST_FILENAME)
                    .size(TEST_CONTENT.length)
                    .content(new ByteArrayInputStream(TEST_CONTENT))
                    .correlationId(TEST_CORRELATION_ID)
                    .uploadedBy(TEST_USER)
                    .build();

            assertThatThrownBy(() -> service.execute(command))
                    .isInstanceOf(FileValidationException.class)
                    .hasMessageContaining("Content type is required");
            then(contentValidator).shouldHaveNoInteractions();
        }

        @Test
        void shouldRejectConfiguredFileSizeLimit() {
            var limitedService = new FileUploadDomainService(
                    contentValidator,
                    fileStorage,
                    eventPublisher,
                    fileRepository,
                    Set.of(TEST_CONTENT_TYPE),
                    5,
                    false,
                    null
            );

            assertThatThrownBy(() -> limitedService.execute(createValidCommand()))
                    .isInstanceOf(FileValidationException.class)
                    .hasMessageContaining("exceeds limit");
            then(contentValidator).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should throw FileValidationException when content cannot be read")
        void shouldThrowWhenContentCannotBeRead() {
            InputStream failingStream = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Read failed");
                }
            };
            var command = UploadFileCommand.builder()
                    .fileName(TEST_FILENAME)
                    .contentType(TEST_CONTENT_TYPE)
                    .size(100)
                    .content(failingStream)
                    .correlationId(TEST_CORRELATION_ID)
                    .uploadedBy(TEST_USER)
                    .build();

            assertThatThrownBy(() -> service.execute(command))
                    .isInstanceOf(FileValidationException.class)
                    .hasMessageContaining("Failed to buffer file content");
        }

        @Test
        @DisplayName("should clear MDC even when exception occurs")
        void shouldClearMdcOnException() {
            var command = createValidCommand();
            lenient().when(contentValidator.validate(any(), any(), anyString()))
                    .thenThrow(new RuntimeException("Validation failed"));

            assertThatThrownBy(() -> service.execute(command))
                    .isInstanceOf(RuntimeException.class);

            assertThat(MDC.get("correlationId")).isNull();
        }
    }

    @Nested
    @DisplayName("execute - storage failures")
    class StorageFailures {

        @Test
        @DisplayName("should propagate StorageException from fileStorage")
        void shouldPropagateStorageException() {
            var command = createValidCommand();
            mockSuccessfulValidation();

            given(fileStorage.store(any(), any(), any(), any(), any()))
                    .willThrow(new io.github.pauszek.fsampgateway.domain.exception.StorageException("S3 error"));

            assertThatThrownBy(() -> service.execute(command))
                    .isInstanceOf(io.github.pauszek.fsampgateway.domain.exception.StorageException.class)
                    .hasMessageContaining("S3 error");

            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("execute - event publish failures")
    class EventPublishFailures {

        @Test
        @DisplayName("should propagate EventPublishException from eventPublisher")
        void shouldPropagateEventPublishException() {
            var command = createValidCommand();
            mockSuccessfulValidation();
            mockSuccessfulStorage();
            mockSuccessfulRepository();

            given(eventPublisher.publish(any()))
                    .willThrow(new io.github.pauszek.fsampgateway.domain.exception.EventPublishException("SNS error"));

            assertThatThrownBy(() -> service.execute(command))
                    .isInstanceOf(io.github.pauszek.fsampgateway.domain.exception.EventPublishException.class)
                    .hasMessageContaining("SNS error");
        }

        @Test
        void shouldPreserveCleanupFailuresAsSuppressedExceptions() {
            mockSuccessfulValidation();
            mockSuccessfulStorage();
            mockSuccessfulRepository();
            var publishFailure = new io.github.pauszek.fsampgateway.domain.exception.EventPublishException(
                    "SNS error"
            );
            var metadataCleanupFailure = new RuntimeException("DynamoDB cleanup failed");
            var storageCleanupFailure = new RuntimeException("S3 cleanup failed");
            given(eventPublisher.publish(any())).willThrow(publishFailure);
            willThrow(metadataCleanupFailure).given(fileRepository).delete(any());
            willThrow(storageCleanupFailure).given(fileStorage).delete(any());

            Throwable thrown = catchThrowable(() -> service.execute(createValidCommand()));

            assertThat(thrown).isSameAs(publishFailure);
            assertThat(thrown.getSuppressed())
                    .containsExactly(metadataCleanupFailure, storageCleanupFailure);
        }
    }


    private UploadFileCommand createValidCommand() {
        return UploadFileCommand.builder()
                .fileName(TEST_FILENAME)
                .contentType(TEST_CONTENT_TYPE)
                .size(TEST_CONTENT.length)
                .content(new ByteArrayInputStream(TEST_CONTENT))
                .correlationId(TEST_CORRELATION_ID)
                .uploadedBy(TEST_USER)
                .build();
    }

    private void mockSuccessfulValidation() {
        given(contentValidator.validate(any(), any(), anyString()))
                .willReturn(ValidationResult.valid(MimeType.of(TEST_CONTENT_TYPE)));
        given(contentValidator.computeChecksum(any()))
                .willReturn(Checksum.sha256("a".repeat(64)));
    }

    private void mockSuccessfulStorage() {
        given(fileStorage.store(any(), any(), any(), any(), any()))
                .willReturn(StorageResult.of(
                        StorageLocation.of("bucket", "key"),
                        EncryptionMetadata.kmsEncrypted("key-id"),
                        "etag-123"
                ));
    }

    private void mockSuccessfulRepository() {
        given(fileRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    private void mockSuccessfulEventPublish() {
        given(eventPublisher.publish(any())).willReturn(TEST_MESSAGE_ID);
    }
}
