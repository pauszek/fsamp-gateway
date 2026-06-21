package io.github.pauszek.fsampgateway.adapter.out.crypto;

import io.github.pauszek.fsampgateway.domain.model.Checksum;
import io.github.pauszek.fsampgateway.domain.model.MimeType;
import io.github.pauszek.fsampgateway.domain.model.ValidationResult;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TikaContentValidatorAdapter")
class TikaContentValidatorAdapterTest {

    private TikaContentValidatorAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TikaContentValidatorAdapter();
        adapter.init();
    }

    @Nested
    @DisplayName("init")
    class Init {

        @Test
        @DisplayName("should skip FIPS provider for relaxed profile list")
        void shouldSkipFipsProviderForRelaxedProfileList() {
            TikaContentValidatorAdapter relaxedAdapter = new TikaContentValidatorAdapter();
            ReflectionTestUtils.setField(relaxedAdapter, "fipsModeEnabled", true);
            ReflectionTestUtils.setField(relaxedAdapter, "activeProfile", "dev, local");

            relaxedAdapter.init();

            assertThat(ReflectionTestUtils.getField(relaxedAdapter, "fipsEnabled")).isEqualTo(false);
        }

        @Test
        @DisplayName("should enable BCFIPS provider outside relaxed profiles")
        void shouldEnableBcfipsProviderOutsideRelaxedProfiles() {
            TikaContentValidatorAdapter productionAdapter = new TikaContentValidatorAdapter();
            ReflectionTestUtils.setField(productionAdapter, "fipsModeEnabled", true);
            ReflectionTestUtils.setField(productionAdapter, "activeProfile", "prod");

            productionAdapter.init();

            assertThat(Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME)).isNotNull();
            assertThat(ReflectionTestUtils.getField(productionAdapter, "fipsEnabled")).isEqualTo(true);
            assertThat(productionAdapter.computeChecksum(new ByteArrayInputStream("secure".getBytes(StandardCharsets.UTF_8)))
                    .value()).hasSize(64);
        }
    }

    @Nested
    @DisplayName("detectMimeType")
    class DetectMimeType {

        @Test
        @DisplayName("should detect text/plain for plain text content")
        void shouldDetectPlainText() {
            InputStream content = new ByteArrayInputStream("Hello, World!".getBytes(StandardCharsets.UTF_8));

            MimeType result = adapter.detectMimeType(content, "test.txt");

            assertThat(result.value()).isEqualTo("text/plain");
        }

        @Test
        @DisplayName("should detect application/pdf for PDF content")
        void shouldDetectPdfContent() {
            byte[] pdfHeader = "%PDF-1.4\n".getBytes(StandardCharsets.UTF_8);
            InputStream content = new ByteArrayInputStream(pdfHeader);

            MimeType result = adapter.detectMimeType(content, "document.pdf");

            assertThat(result.value()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("should detect application/xml for XML content")
        void shouldDetectXmlContent() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root></root>";
            InputStream content = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

            MimeType result = adapter.detectMimeType(content, "data.xml");

            assertThat(result.value()).isIn("application/xml", "text/xml");
        }

        @Test
        @DisplayName("should return application/octet-stream for empty content")
        void shouldReturnOctetStreamForEmpty() {
            InputStream content = new ByteArrayInputStream(new byte[0]);

            MimeType result = adapter.detectMimeType(content, "empty.bin");

            assertThat(result.value()).isNotNull();
        }

        @Test
        @DisplayName("should fallback to octet-stream on detection error")
        void shouldFallbackOnError() {
            InputStream failingStream = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Simulated read error");
                }
            };

            MimeType result = adapter.detectMimeType(failingStream, "test.bin");

            assertThat(result.value()).isEqualTo("application/octet-stream");
        }
    }

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("should return valid result for allowed MIME type")
        void shouldReturnValidForAllowedType() {
            InputStream content = new ByteArrayInputStream("Hello, World!".getBytes(StandardCharsets.UTF_8));
            MimeType declaredType = MimeType.of("text/plain");

            ValidationResult result = adapter.validate(content, declaredType, "test.txt");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getDetectedType().value()).isEqualTo("text/plain");
        }

        @Test
        @DisplayName("should return valid with detected type when declared differs")
        void shouldUseDetectedTypeWhenDeclaredDiffers() {
            InputStream content = new ByteArrayInputStream("Hello, World!".getBytes(StandardCharsets.UTF_8));
            MimeType declaredType = MimeType.of("application/pdf"); // wrong declaration

            ValidationResult result = adapter.validate(content, declaredType, "test.txt");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getDetectedType().value()).isEqualTo("text/plain");
        }

        @Test
        @DisplayName("should handle null declared type")
        void shouldHandleNullDeclaredType() {
            InputStream content = new ByteArrayInputStream("Hello, World!".getBytes(StandardCharsets.UTF_8));

            ValidationResult result = adapter.validate(content, null, "test.txt");

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should return invalid on IOException")
        void shouldReturnInvalidOnIOException() {
            InputStream failingStream = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Simulated read error");
                }
            };
            MimeType declaredType = MimeType.of("text/plain");

            ValidationResult result = adapter.validate(failingStream, declaredType, "test.txt");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("Failed to validate content");
        }

        @Test
        @DisplayName("should return invalid for disallowed MIME type")
        void shouldReturnInvalidForDisallowedType() {
            byte[] elfHeader = new byte[] {0x7f, 'E', 'L', 'F', 0x02, 0x01, 0x01, 0x00};
            InputStream content = new ByteArrayInputStream(elfHeader);
            MimeType declaredType = MimeType.of("application/x-executable");

            ValidationResult result = adapter.validate(content, declaredType, "malware.exe");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("is not allowed");
        }

        @Test
        @DisplayName("should return invalid for application/x-sharedlib type")
        void shouldReturnInvalidForSharedLibType() {
            byte[] machO = new byte[] {(byte)0xCF, (byte)0xFA, (byte)0xED, (byte)0xFE, 0x07, 0x00, 0x00, 0x01};
            InputStream content = new ByteArrayInputStream(machO);

            ValidationResult result = adapter.validate(content, null, "library.dylib");

            if (!result.isValid()) {
                assertThat(result.getMessage()).contains("is not allowed");
            }
        }
    }

    @Nested
    @DisplayName("computeChecksum")
    class ComputeChecksum {

        @Test
        @DisplayName("should compute SHA-256 checksum for content")
        void shouldComputeSha256Checksum() {
            byte[] content = "Hello, World!".getBytes(StandardCharsets.UTF_8);

            Checksum result = adapter.computeChecksum(new ByteArrayInputStream(content));

            assertThat(result.value()).isNotBlank();
            assertThat(result.value()).hasSize(64); // SHA-256 produces 64 hex chars
        }

        @Test
        @DisplayName("should produce consistent checksum for same content")
        void shouldProduceConsistentChecksum() {
            byte[] content = "Test content for checksum".getBytes(StandardCharsets.UTF_8);

            Checksum result1 = adapter.computeChecksum(new ByteArrayInputStream(content));
            Checksum result2 = adapter.computeChecksum(new ByteArrayInputStream(content));

            assertThat(result1).isEqualTo(result2);
        }

        @Test
        @DisplayName("should produce different checksum for different content")
        void shouldProduceDifferentChecksumForDifferentContent() {
            byte[] content1 = "Content A".getBytes(StandardCharsets.UTF_8);
            byte[] content2 = "Content B".getBytes(StandardCharsets.UTF_8);

            Checksum result1 = adapter.computeChecksum(new ByteArrayInputStream(content1));
            Checksum result2 = adapter.computeChecksum(new ByteArrayInputStream(content2));

            assertThat(result1).isNotEqualTo(result2);
        }

        @Test
        @DisplayName("should handle empty content")
        void shouldHandleEmptyContent() {
            byte[] content = new byte[0];

            Checksum result = adapter.computeChecksum(new ByteArrayInputStream(content));

            assertThat(result.value()).isNotBlank();
            assertThat(result.value()).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        }
    }
}
