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

            assertThat(error)
                    .extracting(
                            ApiErrorDto::type,
                            ApiErrorDto::status,
                            ApiErrorDto::error,
                            ApiErrorDto::message,
                            ApiErrorDto::detail,
                            ApiErrorDto::path,
                            ApiErrorDto::correlationId
                    )
                    .containsExactly(
                            "https://api.fsamp.io/errors/validation-error",
                            400,
                            "VALIDATION_ERROR",
                            "Validation failed",
                            "Field 'file' is required",
                            "/api/v1/files/upload",
                            "corr-123"
                    );
        }

        @Test
        @DisplayName("should serialize to JSON correctly")
        void shouldSerializeToJson() throws Exception {
            ApiErrorDto error = ApiErrorDto.builder()
                    .type("https://api.fsamp.io/errors/not-found")
                    .status(404)
                    .error("NOT_FOUND")
                    .message("File not found")
                    .path("/api/v1/files/123")
                    .build();

            String json = objectMapper.writeValueAsString(error);

            assertThat(json).contains("\"status\":404");
            assertThat(json).contains("\"error\":\"NOT_FOUND\"");
            assertThat(json).contains("\"message\":\"File not found\"");
        }

        @Test
        @DisplayName("should include validation errors list")
        void shouldIncludeValidationErrorsList() {
            ApiErrorDto error = ApiErrorDto.builder()
                    .status(400)
                    .error("VALIDATION_ERROR")
                    .message("Validation failed")
                    .validationErrors(List.of(
                            new ApiErrorDto.ValidationErrorDto("file", "must not be null", null),
                            new ApiErrorDto.ValidationErrorDto("size", "must be positive", -1)
                    ))
                    .build();

            assertThat(error.validationErrors())
                    .hasSize(2)
                    .extracting(
                            ApiErrorDto.ValidationErrorDto::field,
                            ApiErrorDto.ValidationErrorDto::rejectedValue
                    )
                    .containsExactly(
                            tuple("file", null),
                            tuple("size", -1)
                    );
        }

        @Test
        @DisplayName("should omit null fields in JSON")
        void shouldOmitNullFieldsInJson() throws Exception {
            ApiErrorDto error = ApiErrorDto.builder()
                    .status(500)
                    .error("INTERNAL_ERROR")
                    .message("Error")
                    .build();

            String json = objectMapper.writeValueAsString(error);

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
            UUID fileId = UUID.randomUUID();
            Instant now = Instant.now();

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

            assertThat(response)
                    .extracting(
                            FileUploadResponseDto::fileId,
                            FileUploadResponseDto::correlationId,
                            FileUploadResponseDto::filename,
                            FileUploadResponseDto::sizeBytes,
                            FileUploadResponseDto::sizeHuman,
                            FileUploadResponseDto::mimeType,
                            FileUploadResponseDto::status,
                            FileUploadResponseDto::uploadedAt
                    )
                    .containsExactly(
                            fileId,
                            "corr-123",
                            "document.pdf",
                            1024000L,
                            "1000.00 KB",
                            "application/pdf",
                            "UPLOADED",
                            now
                    );
        }

        @Test
        @DisplayName("should serialize to JSON correctly")
        void shouldSerializeToJson() throws Exception {
            FileUploadResponseDto response = FileUploadResponseDto.builder()
                    .fileId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                    .filename("test.pdf")
                    .mimeType("application/pdf")
                    .sizeBytes(2048L)
                    .status("UPLOADED")
                    .build();

            String json = objectMapper.writeValueAsString(response);

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
            FileUploadRequestDto request = new FileUploadRequestDto(
                    "corr-123",
                    "Test document",
                    new String[]{"tag1", "tag2"}
            );

            assertThat(request.correlationId()).isEqualTo("corr-123");
            assertThat(request.description()).isEqualTo("Test document");
            assertThat(request.tags()).containsExactly("tag1", "tag2");
        }

        @Test
        @DisplayName("should handle null tags by converting to empty array")
        void shouldHandleNullTags() {
            FileUploadRequestDto request = new FileUploadRequestDto(
                    "a1b2c3d4e5f67890a1b2c3d4e5f67890",
                    "Description",
                    null
            );

            assertThat(request.tags()).isEmpty();
        }

        @Test
        @DisplayName("should defensively copy tags")
        void shouldDefensivelyCopyTags() {
            String[] tags = {"report", "finance"};
            FileUploadRequestDto request = new FileUploadRequestDto("corr-123", "Document", tags);

            tags[0] = "changed";
            String[] returnedTags = request.tags();
            returnedTags[1] = "changed";

            assertThat(request.tags()).containsExactly("report", "finance");
        }

        @Test
        @DisplayName("should compare tags by content")
        void shouldCompareTagsByContent() {
            FileUploadRequestDto first = new FileUploadRequestDto(
                    "corr-123",
                    "Document",
                    new String[]{"report", "finance"}
            );
            FileUploadRequestDto second = new FileUploadRequestDto(
                    "corr-123",
                    "Document",
                    new String[]{"report", "finance"}
            );
            FileUploadRequestDto different = new FileUploadRequestDto(
                    "corr-123",
                    "Document",
                    new String[]{"report"}
            );
            boolean sameInstanceEquals = first.equals(first);

            assertThat(sameInstanceEquals).isTrue();
            assertThat(first)
                    .isEqualTo(second)
                    .hasSameHashCodeAs(second)
                    .isNotEqualTo(different)
                    .isNotEqualTo("corr-123")
                    .hasToString("FileUploadRequestDto[correlationId=corr-123, description=Document, tags=[report, finance]]");
        }
    }

    @Nested
    @DisplayName("ValidationErrorDto")
    class ValidationErrorDtoTest {

        @Test
        @DisplayName("should create validation error with all fields")
        void shouldCreateValidationErrorWithAllFields() {
            var error = new ApiErrorDto.ValidationErrorDto(
                    "fileName",
                    "must not be blank",
                    ""
            );

            assertThat(error)
                    .extracting(
                            ApiErrorDto.ValidationErrorDto::field,
                            ApiErrorDto.ValidationErrorDto::message,
                            ApiErrorDto.ValidationErrorDto::rejectedValue
                    )
                    .containsExactly("fileName", "must not be blank", "");
        }

        @Test
        @DisplayName("should serialize to JSON correctly")
        void shouldSerializeToJson() throws Exception {
            var error = new ApiErrorDto.ValidationErrorDto(
                    "size",
                    "must be positive",
                    -100
            );

            String json = objectMapper.writeValueAsString(error);

            assertThat(json).contains("\"field\":\"size\"");
            assertThat(json).contains("\"rejectedValue\":-100");
        }
    }
}
