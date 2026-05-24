package io.github.pauszek.fsampgateway.domain.command;

import io.github.pauszek.fsampgateway.domain.model.CorrelationId;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UploadFileCommand")
class UploadFileCommandTest {

    @Nested
    @DisplayName("Builder")
    class Builder {

        @Test
        @DisplayName("should build command with all fields")
        void shouldBuildCommandWithAllFields() {
            InputStream content = new ByteArrayInputStream("content".getBytes());

            UploadFileCommand command = UploadFileCommand.builder()
                    .fileName("document.pdf")
                    .contentType("application/pdf")
                    .size(1024L)
                    .content(content)
                    .correlationId("a1b2c3d4e5f67890a1b2c3d4e5f67890")
                    .uploadedBy("user-456")
                    .build();

            assertThat(command.getFileName()).isEqualTo("document.pdf");
            assertThat(command.getContentType()).isEqualTo("application/pdf");
            assertThat(command.getSize()).isEqualTo(1024L);
            assertThat(command.getContent()).isSameAs(content);
            assertThat(command.getCorrelationId()).isEqualTo("a1b2c3d4e5f67890a1b2c3d4e5f67890");
            assertThat(command.getUploadedBy()).isEqualTo("user-456");
        }

        @Test
        @DisplayName("should throw NullPointerException for null fileName")
        void shouldThrowForNullFileName() {
            InputStream content = new ByteArrayInputStream("content".getBytes());

            assertThatThrownBy(() ->
                    UploadFileCommand.builder()
                            .fileName(null)
                            .content(content)
                            .build()
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("File name is required");
        }

        @Test
        @DisplayName("should throw NullPointerException for null content")
        void shouldThrowForNullContent() {
            assertThatThrownBy(() ->
                    UploadFileCommand.builder()
                            .fileName("test.pdf")
                            .content(null)
                            .build()
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Content is required");
        }
    }

    @Nested
    @DisplayName("getCorrelationIdOrGenerate")
    class GetCorrelationIdOrGenerate {

        @Test
        @DisplayName("should use provided correlationId when present")
        void shouldUseProvidedCorrelationId() {
            UploadFileCommand command = UploadFileCommand.builder()
                    .fileName("test.pdf")
                    .content(new ByteArrayInputStream("content".getBytes()))
                    .correlationId("b1c2d3e4f5a67890b1c2d3e4f5a67890")
                    .build();

            CorrelationId result = command.getCorrelationIdOrGenerate();

            assertThat(result.value()).isEqualTo("b1c2d3e4f5a67890b1c2d3e4f5a67890");
        }

        @Test
        @DisplayName("should generate correlationId when not provided")
        void shouldGenerateCorrelationIdWhenNotProvided() {
            UploadFileCommand command = UploadFileCommand.builder()
                    .fileName("test.pdf")
                    .content(new ByteArrayInputStream("content".getBytes()))
                    .build();

            CorrelationId result = command.getCorrelationIdOrGenerate();

            assertThat(result.value()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("should generate correlationId for blank value")
        void shouldGenerateCorrelationIdForBlankValue() {
            UploadFileCommand command = UploadFileCommand.builder()
                    .fileName("test.pdf")
                    .content(new ByteArrayInputStream("content".getBytes()))
                    .correlationId("   ")
                    .build();

            CorrelationId result = command.getCorrelationIdOrGenerate();

            assertThat(result.value()).isNotBlank();
        }
    }
}
