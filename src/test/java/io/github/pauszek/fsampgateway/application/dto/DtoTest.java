package io.github.pauszek.fsampgateway.application.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Application DTOs")
class DtoTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("ApiErrorDto")
    class ApiErrorDtoTest {

        @Test
        @DisplayName("should build error with all fields")
        void shouldBuildErrorWithAllFields() {
            // given/when
            ApiErrorDto error = ApiErrorDto.builder()
                    .type("https://api.fsamp.io/errors/validation-error")
                    .status(400)
                    .error("VALIDATION_ERROR")
                    .message("Validation failed")
                    .detail("Field 'file' is required")
                    .path("/api/v1/files/upload")
                    .correlationId("corr-123")
                    .timestamp(Instant.parse("2024-01-01T12:00:00Z"))
                    .build();

            // then
            assertThat(error.type()).isEqualTo("https://api.fsamp.io/errors/validation-error");
            assertThat(error.status()).isEqualTo(400);
            assertThat(error.error()).isEqualTo("VALIDATION_ERROR");
            assertThat(error.message()).isEqualTo("Validation failed");
            assertThat(error.detail()).isEqualTo("Field 'file' is required");
            assertThat(error.path()).isEqualTo("/api/v1/files/upload");
            assertThat(error.correlationId()).isEqualTo("corr-123");
        }

        @Test
        @DisplayName("should serialize to JSON correctly")
        void shouldSerializeToJson() throws Exception {
            // given
            ApiErrorDto error = ApiErrorDto.builder()
                    .type("https://api.fsamp.io/errors/not-found")
                    .status(404)
                    .error("NOT_FOUND")
                    .message("File not found")
                    .path("/api/v1/files/123")
                    .build();

            // when
            String json = objectMapper.writeValueAsString(error);

            // then
            assertThat(json).contains("\"status\":404");
            assertThat(json).contains("\"error\":\"NOT_FOUND\"");
            assertThat(json).contains("\"message\":\"File not found\"");
        }

        @Test
        @DisplayName("should include validation errors list")
        void shouldIncludeValidationErrorsList() {
            // given/when
            ApiErrorDto error = ApiErrorDto.builder()
                    .status(400)
                    .error("VALIDATION_ERROR")
                    .message("Validation failed")
                    .validationErrors(List.of(
                            new ApiErrorDto.ValidationErrorDto("file", "must not be null", null),
                            new ApiErrorDto.ValidationErrorDto("size", "must be positive", -1)
                    ))
                    .build();

            // then
            assertThat(error.validationErrors()).hasSize(2);
            assertThat(error.validationErrors().get(0).field()).isEqualTo("file");
            assertThat(error.validationErrors().get(1).rejectedValue()).isEqualTo(-1);
        }

        @Test
        @DisplayName("should omit null fields in JSON")
        void shouldOmitNullFieldsInJson() throws Exception {
            // given
            ApiErrorDto error = ApiErrorDto.builder()
                    .status(500)
                    .error("INTERNAL_ERROR")
                    .message("Error")
                    .build();

            // when
            String json = objectMapper.writeValueAsString(error);

            // then
            assertThat(json).doesNotContain("\"detail\"");
            assertThat(json).doesNotContain("\"validationErrors\"");
        }
    }

    @Nested
    @DisplayName("FileUploadResponseDto")
    class FileUploadResponseDtoTest {

        @Test
        @DisplayName("should build response with all fields")
        void shouldBuildResponseWithAllFields() {
            // given
            UUID fileId = UUID.randomUUID();
            Instant now = Instant.now();

            // when
            FileUploadResponseDto response = FileUploadResponseDto.builder()
                    .fileId(fileId)
                    .correlationId("corr-123")
                    .filename("document.pdf")
                    .sizeBytes(1024000L)
                    .sizeHuman("1000.00 KB")
                    .mimeType("application/pdf")
                    .checksum("sha256:abc123")
                    .status("UPLOADED")
                    .statusDescription("File stored, awaiting processing")
                    .uploadedAt(now)
                    .message("File uploaded successfully")
                    .build();

            // then
            assertThat(response.fileId()).isEqualTo(fileId);
            assertThat(response.correlationId()).isEqualTo("corr-123");
            assertThat(response.filename()).isEqualTo("document.pdf");
            assertThat(response.sizeBytes()).isEqualTo(1024000L);
            assertThat(response.sizeHuman()).isEqualTo("1000.00 KB");
            assertThat(response.mimeType()).isEqualTo("application/pdf");
            assertThat(response.status()).isEqualTo("UPLOADED");
            assertThat(response.uploadedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("should serialize to JSON correctly")
        void shouldSerializeToJson() throws Exception {
            // given
            FileUploadResponseDto response = FileUploadResponseDto.builder()
                    .fileId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                    .filename("test.pdf")
                    .mimeType("application/pdf")
                    .sizeBytes(2048L)
                    .status("UPLOADED")
                    .build();

            // when
            String json = objectMapper.writeValueAsString(response);

            // then
            assertThat(json).contains("\"fileId\":\"550e8400-e29b-41d4-a716-446655440000\"");
            assertThat(json).contains("\"filename\":\"test.pdf\"");
            assertThat(json).contains("\"mimeType\":\"application/pdf\"");
            assertThat(json).contains("\"sizeBytes\":2048");
        }
    }

    @Nested
    @DisplayName("FileUploadRequestDto")
    class FileUploadRequestDtoTest {

        @Test
        @DisplayName("should create request with all fields")
        void shouldCreateRequestWithAllFields() {
            // when
            FileUploadRequestDto request = new FileUploadRequestDto(
                    "corr-123",
                    "Test document",
                    new String[]{"tag1", "tag2"}
            );

            // then
            assertThat(request.correlationId()).isEqualTo("corr-123");
            assertThat(request.description()).isEqualTo("Test document");
            assertThat(request.tags()).containsExactly("tag1", "tag2");
        }

        @Test
        @DisplayName("should handle null tags by converting to empty array")
        void shouldHandleNullTags() {
            // when
            FileUploadRequestDto request = new FileUploadRequestDto(
                    "a1b2c3d4e5f67890a1b2c3d4e5f67890",
                    "Description",
                    null
            );

            // then
            assertThat(request.tags()).isEmpty();
        }
    }

    @Nested
    @DisplayName("ValidationErrorDto")
    class ValidationErrorDtoTest {

        @Test
        @DisplayName("should create validation error with all fields")
        void shouldCreateValidationErrorWithAllFields() {
            // when
            var error = new ApiErrorDto.ValidationErrorDto(
                    "fileName",
                    "must not be blank",
                    ""
            );

            // then
            assertThat(error.field()).isEqualTo("fileName");
            assertThat(error.message()).isEqualTo("must not be blank");
            assertThat(error.rejectedValue()).isEqualTo("");
        }

        @Test
        @DisplayName("should serialize to JSON correctly")
        void shouldSerializeToJson() throws Exception {
            // given
            var error = new ApiErrorDto.ValidationErrorDto(
                    "size",
                    "must be positive",
                    -100
            );

            // when
            String json = objectMapper.writeValueAsString(error);

            // then
            assertThat(json).contains("\"field\":\"size\"");
            assertThat(json).contains("\"rejectedValue\":-100");
        }
    }
}
