package io.github.pauszek.fsampgateway.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("KMS Encryption Integration Tests")
@Tag("integration")
class KmsEncryptionIntegrationTest extends BaseIntegrationTest {

    private KmsClient kmsClient;

    @BeforeEach
    void setUpKms() {
        kmsClient = KmsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(software.amazon.awssdk.regions.Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")))
                .build();
    }

    @Nested
    @DisplayName("KMS Key Operations")
    class KmsKeyOperations {

        @Test
        @DisplayName("should describe the shared integration test KMS key")
        void shouldDescribeKmsKey() {
            DescribeKeyResponse response = kmsClient.describeKey(DescribeKeyRequest.builder()
                    .keyId(kmsKeyId)
                    .build());

            assertThat(response.keyMetadata().keyId()).isEqualTo(kmsKeyId);
            assertThat(response.keyMetadata().keyState()).isEqualTo(KeyState.ENABLED);
            assertThat(response.keyMetadata().keyUsage()).isEqualTo(KeyUsageType.ENCRYPT_DECRYPT);
        }

        @Test
        @DisplayName("should resolve KMS alias to correct key")
        void shouldResolveKmsAlias() {
            DescribeKeyResponse response = kmsClient.describeKey(DescribeKeyRequest.builder()
                    .keyId(TEST_KMS_ALIAS)
                    .build());

            assertThat(response.keyMetadata().keyId()).isEqualTo(kmsKeyId);
        }

        @Test
        @DisplayName("should create a new KMS key for testing isolation")
        void shouldCreateNewKmsKey() {
            CreateKeyResponse response = kmsClient.createKey(CreateKeyRequest.builder()
                    .description("Test key - " + UUID.randomUUID())
                    .keyUsage(KeyUsageType.ENCRYPT_DECRYPT)
                    .build());

            assertThat(response.keyMetadata().keyId()).isNotBlank();
            assertThat(response.keyMetadata().keyState()).isEqualTo(KeyState.ENABLED);
        }
    }

    @Nested
    @DisplayName("Envelope Encryption (Data Keys)")
    class EnvelopeEncryption {

        @Test
        @DisplayName("should generate data key for envelope encryption")
        void shouldGenerateDataKey() {
            GenerateDataKeyResponse response = kmsClient.generateDataKey(GenerateDataKeyRequest.builder()
                    .keyId(kmsKeyId)
                    .keySpec(DataKeySpec.AES_256)
                    .build());

            assertThat(response.plaintext()).isNotNull();
            assertThat(response.ciphertextBlob()).isNotNull();
            assertThat(response.plaintext().asByteArray()).hasSize(32); // AES-256 = 32 bytes
            assertThat(response.keyId()).contains(kmsKeyId);
        }

        @Test
        @DisplayName("should decrypt data key ciphertext back to original plaintext")
        void shouldDecryptDataKey() {
            GenerateDataKeyResponse dataKey = kmsClient.generateDataKey(GenerateDataKeyRequest.builder()
                    .keyId(kmsKeyId)
                    .keySpec(DataKeySpec.AES_256)
                    .build());

            DecryptResponse decrypted = kmsClient.decrypt(DecryptRequest.builder()
                    .keyId(kmsKeyId)
                    .ciphertextBlob(dataKey.ciphertextBlob())
                    .build());

            assertThat(decrypted.plaintext().asByteArray())
                    .isEqualTo(dataKey.plaintext().asByteArray());
        }

        @Test
        @DisplayName("should encrypt and decrypt arbitrary data via KMS")
        void shouldEncryptDecryptData() {
            byte[] plaintext = "Sensitive FSAMP metadata for FedRAMP SC-28".getBytes(StandardCharsets.UTF_8);

            EncryptResponse encrypted = kmsClient.encrypt(EncryptRequest.builder()
                    .keyId(kmsKeyId)
                    .plaintext(SdkBytes.fromByteArray(plaintext))
                    .build());

            assertThat(encrypted.ciphertextBlob().asByteArray())
                    .isNotEqualTo(plaintext);

            DecryptResponse decrypted = kmsClient.decrypt(DecryptRequest.builder()
                    .keyId(kmsKeyId)
                    .ciphertextBlob(encrypted.ciphertextBlob())
                    .build());

            assertThat(decrypted.plaintext().asByteArray())
                    .isEqualTo(plaintext);
        }
    }

    @Nested
    @DisplayName("S3 SSE-KMS Verification")
    class S3SseKmsVerification {

        @Test
        @DisplayName("should upload file with SSE-KMS encryption and verify headers")
        void shouldUploadFileWithKMSEncryption() {
            String key = "sse-test/" + UUID.randomUUID() + "/encrypted-file.txt";
            byte[] content = "SSE-KMS encrypted content for FedRAMP SC-28".getBytes(StandardCharsets.UTF_8);

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(TEST_BUCKET)
                            .key(key)
                            .contentType("text/plain")
                            .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                            .ssekmsKeyId(kmsKeyId)
                            .build(),
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(content)
            );

            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(TEST_BUCKET)
                    .key(key)
                    .build());

            assertThat(head.serverSideEncryption())
                    .as("Object must be encrypted with SSE-KMS")
                    .isEqualTo(ServerSideEncryption.AWS_KMS);
            assertThat(head.ssekmsKeyId())
                    .as("SSE-KMS key ID must be set")
                    .isNotBlank();
        }

        @Test
        @DisplayName("bucket default encryption should use SSE-KMS")
        void bucketDefaultEncryptionShouldUseKms() {
            GetBucketEncryptionResponse encryption = s3Client.getBucketEncryption(
                    GetBucketEncryptionRequest.builder()
                            .bucket(TEST_BUCKET)
                            .build());

            assertThat(encryption.serverSideEncryptionConfiguration().rules()).isNotEmpty();
            ServerSideEncryptionRule rule = encryption.serverSideEncryptionConfiguration().rules().get(0);
            assertThat(rule.applyServerSideEncryptionByDefault().sseAlgorithm())
                    .isEqualTo(ServerSideEncryption.AWS_KMS);
            assertThat(rule.applyServerSideEncryptionByDefault().kmsMasterKeyID())
                    .contains(kmsKeyId);
            assertThat(rule.bucketKeyEnabled()).isTrue();
        }

        @Test
        @DisplayName("bucket versioning should be enabled")
        void bucketVersioningShouldBeEnabled() {
            GetBucketVersioningResponse versioning = s3Client.getBucketVersioning(
                    GetBucketVersioningRequest.builder()
                            .bucket(TEST_BUCKET)
                            .build());

            assertThat(versioning.status()).isEqualTo(BucketVersioningStatus.ENABLED);
        }
    }
}
