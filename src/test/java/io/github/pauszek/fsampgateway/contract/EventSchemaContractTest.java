package io.github.pauszek.fsampgateway.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.*;
import io.github.pauszek.fsampgateway.domain.event.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("Event Schema Contract Tests")
class EventSchemaContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    private static final Instant EVENT_TIMESTAMP = Instant.parse("2026-01-15T10:00:00Z");

    private static JsonSchema schema;
    private static boolean schemaAvailable = false;

    @BeforeAll
    static void loadSchema() {
        Path[] schemaPaths = {
                Path.of("schema/event.schema.json"),           // CI - downloaded
                Path.of("../fsamp-event-schema/event.schema.json")  // Local dev - sibling repo
        };

        for (Path path : schemaPaths) {
            if (Files.exists(path)) {
                try {
                    JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
                    schema = factory.getSchema(Files.newInputStream(path));
                    schemaAvailable = true;
                    System.out.println("Loaded schema from: " + path);
                    return;
                } catch (IOException e) {
                    System.err.println("Failed to load schema from " + path + ": " + e.getMessage());
                }
            }
        }

        System.out.println("Schema not found. Run './scripts/download-schema.sh' or ensure sibling repo exists.");
    }

    @BeforeEach
    void checkSchemaAvailable() {
        assumeTrue(schemaAvailable, "Schema not available - skipping contract tests");
    }

    @Nested
    @DisplayName("FileUploadedEvent Contract")
    class FileUploadedEventContract {

        @Test
        @DisplayName("should produce valid JSON according to schema v1.1.2")
        void shouldProduceValidJson() throws Exception {
            FileUploadedEvent event = createSampleEvent();

            String json = OBJECT_MAPPER.writeValueAsString(event);
            JsonNode jsonNode = OBJECT_MAPPER.readTree(json);

            Set<ValidationMessage> errors = schema.validate(jsonNode);
            assertThat(errors)
                    .as("Event JSON should be valid against schema. Errors: %s", errors)
                    .isEmpty();
        }

        @Test
        @DisplayName("should include all required fields per schema v1.1.2")
        void shouldIncludeAllRequiredFields() throws Exception {
            FileUploadedEvent event = createSampleEvent();

            String json = OBJECT_MAPPER.writeValueAsString(event);
            JsonNode jsonNode = OBJECT_MAPPER.readTree(json);

            assertThat(jsonNode.has("schemaVersion")).isTrue();
            assertThat(jsonNode.has("fileId")).isTrue();
            assertThat(jsonNode.has("eventId")).isTrue();
            assertThat(jsonNode.has("correlationId")).isTrue();
            assertThat(jsonNode.has("timestamp")).isTrue();
            assertThat(jsonNode.has("source")).isTrue();
            assertThat(jsonNode.has("eventType")).isTrue();
            assertThat(jsonNode.has("fileMetadata")).isTrue();
            assertThat(jsonNode.has("storageLocation")).isTrue();
            assertThat(jsonNode.has("securityContext")).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "schemaVersion, 1.1.2",
                "source, fsamp-gateway",
                "eventType, FILE_UPLOADED"
        })
        @DisplayName("should include expected top-level values")
        void shouldIncludeExpectedTopLevelValues(String fieldName, String expectedValue) throws Exception {
            FileUploadedEvent event = createSampleEvent();

            String json = OBJECT_MAPPER.writeValueAsString(event);
            JsonNode jsonNode = OBJECT_MAPPER.readTree(json);

            assertThat(jsonNode.get(fieldName).asText()).isEqualTo(expectedValue);
        }

        @ParameterizedTest
        @ValueSource(strings = {"FILE_UPLOADED", "FILE_SCANNED", "ANALYSIS_COMPLETED", "PROCESSING_FAILED"})
        @DisplayName("should accept valid event types from schema")
        void shouldAcceptValidEventTypes(String eventType) throws Exception {
            FileUploadedEvent event = new FileUploadedEvent(
                    FileUploadedEvent.SCHEMA_VERSION,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    EVENT_TIMESTAMP,
                    FileUploadedEvent.EVENT_SOURCE,
                    eventType,
                    FilePayload.of("test.pdf", 1024L, "application/pdf", 
                            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                    StoragePayload.of("fsamp-bucket", "uploads/test.pdf"),
                    SecurityPayload.of(true, "AES/GCM/NoPadding", 
                            "arn:aws:kms:eu-central-1:123456789012:key/12345678-1234-1234-1234-123456789012")
            );

            String json = OBJECT_MAPPER.writeValueAsString(event);
            JsonNode jsonNode = OBJECT_MAPPER.readTree(json);

            Set<ValidationMessage> errors = schema.validate(jsonNode);
            assertThat(errors).isEmpty();
        }
    }

    @Nested
    @DisplayName("FileMetadata Contract")
    class FileMetadataContract {

        @Test
        @DisplayName("should include all required fileMetadata fields")
        void shouldIncludeRequiredFields() throws Exception {
            FileUploadedEvent event = createSampleEvent();

            String json = OBJECT_MAPPER.writeValueAsString(event);
            JsonNode fileMetadata = OBJECT_MAPPER.readTree(json).get("fileMetadata");

            assertThat(fileMetadata.has("originalFilename")).isTrue();
            assertThat(fileMetadata.has("fileSizeBytes")).isTrue();
            assertThat(fileMetadata.has("checksumSHA256")).isTrue();
        }

        @Test
        @DisplayName("should have valid SHA-256 checksum format")
        void shouldHaveValidChecksumFormat() throws Exception {
            FileUploadedEvent event = createSampleEvent();

            String json = OBJECT_MAPPER.writeValueAsString(event);
            JsonNode fileMetadata = OBJECT_MAPPER.readTree(json).get("fileMetadata");
            String checksum = fileMetadata.get("checksumSHA256").asText();

            assertThat(checksum).matches("^[a-f0-9]{64}$");
        }

        @Test
        @DisplayName("should enforce max file size of 100MB")
        void shouldEnforceMaxFileSize() throws Exception {
            FileUploadedEvent event = new FileUploadedEvent(
                    FileUploadedEvent.SCHEMA_VERSION,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    EVENT_TIMESTAMP,
                    FileUploadedEvent.EVENT_SOURCE,
                    FileUploadedEvent.EVENT_TYPE,
                    FilePayload.of("large.pdf", 104857600L, "application/pdf",
                            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                    StoragePayload.of("fsamp-bucket", "uploads/large.pdf"),
                    SecurityPayload.of(true, "AES/GCM/NoPadding",
                            "arn:aws:kms:eu-central-1:123456789012:key/12345678-1234-1234-1234-123456789012")
            );

            String json = OBJECT_MAPPER.writeValueAsString(event);
            JsonNode jsonNode = OBJECT_MAPPER.readTree(json);

            Set<ValidationMessage> errors = schema.validate(jsonNode);
            assertThat(errors).isEmpty();
        }
    }

    @Nested
    @DisplayName("SecurityContext Contract - FIPS 140-3")
    class SecurityContextContract {

        @Test
        @DisplayName("should enforce encryption is always true")
        void shouldEnforceEncryption() throws Exception {
            FileUploadedEvent event = createSampleEvent();

            String json = OBJECT_MAPPER.writeValueAsString(event);
            JsonNode securityContext = OBJECT_MAPPER.readTree(json).get("securityContext");

            assertThat(securityContext.has("isEncrypted")).isTrue();
            assertThat(securityContext.get("isEncrypted").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should only allow AES-256-GCM algorithm (FIPS 140-3)")
        void shouldOnlyAllowAesGcm() throws Exception {
            FileUploadedEvent event = createSampleEvent();

            String json = OBJECT_MAPPER.writeValueAsString(event);
            JsonNode securityContext = OBJECT_MAPPER.readTree(json).get("securityContext");

            String algorithm = securityContext.get("encryptionAlgorithm").asText();
            assertThat(algorithm).isEqualTo("AES/GCM/NoPadding");
        }

        @Test
        @DisplayName("should require KMS key ARN")
        void shouldRequireKmsKey() throws Exception {
            FileUploadedEvent event = createSampleEvent();

            String json = OBJECT_MAPPER.writeValueAsString(event);
            JsonNode securityContext = OBJECT_MAPPER.readTree(json).get("securityContext");

            assertThat(securityContext.has("kmsKeyId")).isTrue();
            String kmsKeyId = securityContext.get("kmsKeyId").asText();
            assertThat(kmsKeyId).startsWith("arn:aws:kms:");
        }

        @Test
        @DisplayName("should have valid KMS ARN format")
        void shouldHaveValidKmsArnFormat() throws Exception {
            FileUploadedEvent event = createSampleEvent();

            String json = OBJECT_MAPPER.writeValueAsString(event);
            JsonNode securityContext = OBJECT_MAPPER.readTree(json).get("securityContext");
            String kmsKeyId = securityContext.get("kmsKeyId").asText();

            assertThat(kmsKeyId).matches("^arn:aws:kms:[a-z0-9-]+:\\d{12}:key/[a-f0-9-]{36}$");
        }
    }

    @Nested
    @DisplayName("Schema Validation - Negative Tests")
    class SchemaValidation {

        @ParameterizedTest
        @CsvSource({
                "invalid event type, eventType",
                "missing source, source",
                "AES-CBC algorithm, encryptionAlgorithm",
                "unencrypted file, isEncrypted",
                "oversized file, fileSizeBytes"
        })
        @DisplayName("should reject invalid schema cases")
        void shouldRejectInvalidSchemaCases(String scenario, String expectedErrorFragment) throws Exception {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(invalidJsonFor(scenario));
            Set<ValidationMessage> errors = schema.validate(jsonNode);

            assertThat(errors).isNotEmpty();
            assertThat(errors.toString()).containsIgnoringCase(expectedErrorFragment);
        }

        @Test
        @DisplayName("should reject legacy event without fileId")
        void shouldRejectLegacyEventWithoutFileId() throws Exception {
            FileUploadedEvent event = createSampleEvent();
            ObjectNode jsonNode = (ObjectNode) OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(event));
            jsonNode.remove("fileId");

            Set<ValidationMessage> errors = schema.validate(jsonNode);

            assertThat(errors).isNotEmpty();
            assertThat(errors.toString()).containsIgnoringCase("fileId");
        }

        private String invalidJsonFor(String scenario) {
            return switch (scenario) {
                case "invalid event type" -> validJsonTemplate("""
                        "source": "fsamp-gateway",
                        "eventType": "INVALID_TYPE",
                        """, """
                        "originalFilename": "test.pdf",
                        "fileSizeBytes": 1024,
                        """, """
                        "isEncrypted": true,
                        "encryptionAlgorithm": "AES/GCM/NoPadding",
                        """);
                case "missing source" -> validJsonTemplate("""
                        "eventType": "FILE_UPLOADED",
                        """, """
                        "originalFilename": "test.pdf",
                        "fileSizeBytes": 1024,
                        """, """
                        "isEncrypted": true,
                        "encryptionAlgorithm": "AES/GCM/NoPadding",
                        """);
                case "AES-CBC algorithm" -> validJsonTemplate("""
                        "source": "fsamp-gateway",
                        "eventType": "FILE_UPLOADED",
                        """, """
                        "originalFilename": "test.pdf",
                        "fileSizeBytes": 1024,
                        """, """
                        "isEncrypted": true,
                        "encryptionAlgorithm": "AES/CBC/PKCS5Padding",
                        """);
                case "unencrypted file" -> validJsonTemplate("""
                        "source": "fsamp-gateway",
                        "eventType": "FILE_UPLOADED",
                        """, """
                        "originalFilename": "test.pdf",
                        "fileSizeBytes": 1024,
                        """, """
                        "isEncrypted": false,
                        "encryptionAlgorithm": "AES/GCM/NoPadding",
                        """);
                case "oversized file" -> validJsonTemplate("""
                        "source": "fsamp-gateway",
                        "eventType": "FILE_UPLOADED",
                        """, """
                        "originalFilename": "huge.pdf",
                        "fileSizeBytes": 104857601,
                        """, """
                        "isEncrypted": true,
                        "encryptionAlgorithm": "AES/GCM/NoPadding",
                        """);
                default -> throw new IllegalArgumentException("Unknown schema scenario: " + scenario);
            };
        }

        private String validJsonTemplate(String topLevelFields, String fileMetadataFields, String securityFields) {
            return """
                    {
                        "schemaVersion": "1.1.2",
                        "fileId": "550e8400-e29b-41d4-a716-446655440002",
                        "eventId": "550e8400-e29b-41d4-a716-446655440000",
                        "correlationId": "550e8400-e29b-41d4-a716-446655440001",
                        "timestamp": "2026-01-01T12:00:00Z",
                    %s
                        "fileMetadata": {
                    %s
                            "checksumSHA256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                        },
                        "storageLocation": {
                            "bucketName": "fsamp-bucket",
                            "objectKey": "key"
                        },
                        "securityContext": {
                    %s
                            "kmsKeyId": "arn:aws:kms:eu-central-1:123456789012:key/12345678-1234-1234-1234-123456789012"
                        }
                    }
                    """.formatted(topLevelFields, fileMetadataFields, securityFields);
        }
    }

    private FileUploadedEvent createSampleEvent() {
        return new FileUploadedEvent(
                FileUploadedEvent.SCHEMA_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                EVENT_TIMESTAMP,
                FileUploadedEvent.EVENT_SOURCE,
                FileUploadedEvent.EVENT_TYPE,
                FilePayload.of(
                        "document.pdf", 
                        2048L, 
                        "application/pdf",
                        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                ),
                StoragePayload.of("fsamp-secure-bucket", "uploads/2026/01/document.pdf"),
                SecurityPayload.of(
                        true, 
                        "AES/GCM/NoPadding", 
                        "arn:aws:kms:eu-central-1:123456789012:key/12345678-1234-1234-1234-123456789012"
                )
        );
    }
}
