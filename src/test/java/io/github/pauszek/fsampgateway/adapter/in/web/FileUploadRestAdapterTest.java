package io.github.pauszek.fsampgateway.adapter.in.web;

import io.github.pauszek.fsampgateway.application.dto.FileUploadResponseDto;
import io.github.pauszek.fsampgateway.application.mapper.FileMapper;
import io.github.pauszek.fsampgateway.domain.command.UploadFileCommand;
import io.github.pauszek.fsampgateway.domain.model.*;
import io.github.pauszek.fsampgateway.domain.port.in.DeleteFileUseCase;
import io.github.pauszek.fsampgateway.domain.port.in.GetFileUseCase;
import io.github.pauszek.fsampgateway.domain.port.in.UploadFileUseCase;
import io.github.pauszek.fsampgateway.infrastructure.security.cognito.CurrentUserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileUploadRestAdapter")
class FileUploadRestAdapterTest {

    @Mock
    private UploadFileUseCase uploadFileUseCase;

    @Mock
    private GetFileUseCase getFileUseCase;

    @Mock
    private DeleteFileUseCase deleteFileUseCase;

    @Mock
    private FileMapper fileMapper;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private FileUploadRestAdapter adapter;

    @Captor
    private ArgumentCaptor<UploadFileCommand> commandCaptor;

    private static final String USER_ID = "user-123";
    private static final String FILE_ID = "file-abc-123";

    @Nested
    @DisplayName("uploadFile")
    class UploadFile {

        @BeforeEach
        void setUpRequestContext() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/files/upload");
            request.setScheme("http");
            request.setServerName("localhost");
            request.setServerPort(8080);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        }

        @AfterEach
        void clearRequestContext() {
            RequestContextHolder.resetRequestAttributes();
        }

        @Test
        @DisplayName("should upload file successfully with valid data")
        void shouldUploadFileSuccessfully() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-document.pdf",
                    "application/pdf",
                    "PDF content".getBytes()
            );

            given(currentUserService.getCurrentUser())
                    .willReturn(Optional.of(createTestUser()));
            given(uploadFileUseCase.execute(any())).willReturn(createUploadedFile());
            given(fileMapper.toResponseDto(any())).willReturn(createResponseDto());

            ResponseEntity<FileUploadResponseDto> response = adapter.uploadFile(file, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().fileId()).isNotNull();
        }

        @Test
        @DisplayName("should create command with correct parameters")
        void shouldCreateCommandWithCorrectParameters() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "report.pdf",
                    "application/pdf",
                    "Report content".getBytes()
            );

            given(currentUserService.getCurrentUser())
                    .willReturn(Optional.of(createTestUser()));
            given(uploadFileUseCase.execute(any())).willReturn(createUploadedFile());
            given(fileMapper.toResponseDto(any())).willReturn(createResponseDto());

            adapter.uploadFile(file, null);

            then(uploadFileUseCase).should().execute(commandCaptor.capture());
            UploadFileCommand command = commandCaptor.getValue();
            assertThat(command.getFileName()).isEqualTo("report.pdf");
            assertThat(command.getContentType()).isEqualTo("application/pdf");
            assertThat(command.getSize()).isEqualTo("Report content".getBytes().length);
            assertThat(command.getUploadedBy()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("should throw when user not found in security context")
        void shouldThrowWhenUserNotFound() {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.pdf",
                    "application/pdf",
                    "content".getBytes()
            );

            given(currentUserService.getCurrentUser()).willReturn(Optional.empty());

            assertThatThrownBy(() -> adapter.uploadFile(file, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("should pass correlationId from request")
        void shouldPassCorrelationIdFromRequest() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.pdf",
                    "application/pdf",
                    "content".getBytes()
            );
            var request = new io.github.pauszek.fsampgateway.application.dto.FileUploadRequestDto(
                    "custom-corr-id", null, null
            );

            given(currentUserService.getCurrentUser())
                    .willReturn(Optional.of(createTestUser()));
            given(uploadFileUseCase.execute(any())).willReturn(createUploadedFile());
            given(fileMapper.toResponseDto(any())).willReturn(createResponseDto());

            adapter.uploadFile(file, request);

            then(uploadFileUseCase).should().execute(commandCaptor.capture());
            assertThat(commandCaptor.getValue().getCorrelationId()).isEqualTo("custom-corr-id");
        }
    }

    @Nested
    @DisplayName("getFile")
    class GetFile {

        @Test
        @DisplayName("should return 200 OK with file data")
        void shouldReturnFileSuccessfully() {
            SecureFile file = createUploadedFile();
            String fileId = file.getId().toString();
            given(currentUserService.getCurrentUserId()).willReturn(Optional.of(USER_ID));
            given(getFileUseCase.getByIdOrThrow(any(FileId.class))).willReturn(file);
            given(fileMapper.toResponseDto(file)).willReturn(createResponseDto());

            ResponseEntity<FileUploadResponseDto> response = adapter.getFile(fileId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }
    }

    @Nested
    @DisplayName("deleteFile")
    class DeleteFile {

        @Test
        @DisplayName("should return 204 NO_CONTENT on successful delete")
        void shouldDeleteFileSuccessfully() {
            FileId fileId = FileId.generate();
            given(currentUserService.getCurrentUserId()).willReturn(Optional.of(USER_ID));
            willDoNothing().given(deleteFileUseCase).execute(any(FileId.class));

            ResponseEntity<Void> response = adapter.deleteFile(fileId.toString());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            then(deleteFileUseCase).should().execute(any(FileId.class));
        }
    }


    private SecureFile createUploadedFile() {
        return SecureFile.createPending(
                FileName.of("test-document.pdf"),
                MimeType.of("application/pdf"),
                FileSize.of(1024L),
                CorrelationId.of("a1b2c3d4e5f67890a1b2c3d4e5f67890"),
                USER_ID
        ).markAsUploaded(
                StorageLocation.of("bucket", "key"),
                EncryptionMetadata.kmsEncrypted("kms-key-id"),
                Checksum.sha256("a".repeat(64))
        );
    }

    private UserPrincipal createTestUser() {
        return new UserPrincipal(
                USER_ID,
                "user@test.com",
                "Test User",
                Set.of("USERS"),
                Set.of("files.write"),
                null,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        );
    }

    private FileUploadResponseDto createResponseDto() {
        return FileUploadResponseDto.builder()
                .fileId(java.util.UUID.randomUUID())
                .filename("test-document.pdf")
                .mimeType("application/pdf")
                .sizeBytes(1024L)
                .sizeHuman("1.00 KB")
                .status("UPLOADED")
                .correlationId("a1b2c3d4e5f67890a1b2c3d4e5f67890")
                .uploadedAt(Instant.now())
                .message("File uploaded successfully")
                .build();
    }
}
