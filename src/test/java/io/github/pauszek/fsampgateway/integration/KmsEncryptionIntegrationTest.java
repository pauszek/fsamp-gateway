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

/**
 * KMS Encryption Integration Tests with LocalStack Pro.
 *
 * Verifies:
 * - KMS key creation and management
 * - Data key generation (envelope encryption)
 * - S3 SSE-KMS encryption verification on uploaded objects
 *
 * FedRAMP Controls: SC-12 (Cryptographic Key Management), SC-28 (Data at Rest)
 */
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
            // when
            DescribeKeyResponse response = kmsClient.describeKey(DescribeKeyRequest.builder()
                    .keyId(kmsKeyId)
                    .build());

            // then
            assertThat(response.keyMetadata().keyId()).isEqualTo(kmsKeyId);
            assertThat(response.keyMetadata().keyState()).isEqualTo(KeyState.ENABLED);
            assertThat(response.keyMetadata().keyUsage()).isEqualTo(KeyUsageType.ENCRYPT_DECRYPT);
        }

        @Test
        @DisplayName("should resolve KMS alias to correct key")
        void shouldResolveKmsAlias() {
            // when
            DescribeKeyResponse response = kmsClient.describeKey(DescribeKeyRequest.builder()
                    .keyId(TEST_KMS_ALIAS)
                    .build());

            // then
            assertThat(response.keyMetadata().keyId()).isEqualTo(kmsKeyId);
        }

        @Test
        @DisplayName("should create a new KMS key for testing isolation")
        void shouldCreateNewKmsKey() {
            // when
            CreateKeyResponse response = kmsClient.createKey(CreateKeyRequest.builder()
                    .description("Test key - " + UUID.randomUUID())
                    .keyUsage(KeyUsageType.ENCRYPT_DECRYPT)
                    .build());

            // then
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
            // when
            GenerateDataKeyResponse response = kmsClient.generateDataKey(GenerateDataKeyRequest.builder()
                    .keyId(kmsKeyId)
                    .keySpec(DataKeySpec.AES_256)
                    .build());

            // then
            assertThat(response.plaintext()).isNotNull();
            assertThat(response.ciphertextBlob()).isNotNull();
            assertThat(response.plaintext().asByteArray()).hasSize(32); // AES-256 = 32 bytes
            assertThat(response.keyId()).contains(kmsKeyId);
        }

        @Test
        @DisplayName("should decrypt data key ciphertext back to original plaintext")
        void shouldDecryptDataKey() {
            // given
            GenerateDataKeyResponse dataKey = kmsClient.generateDataKey(GenerateDataKeyRequest.builder()
                    .keyId(kmsKeyId)
                    .keySpec(DataKeySpec.AES_256)
                    .build());

            // when - decrypt the encrypted data key
            DecryptResponse decrypted = kmsClient.decrypt(DecryptRequest.builder()
                    .keyId(kmsKeyId)
                    .ciphertextBlob(dataKey.ciphertextBlob())
                    .build());

            // then - decrypted plaintext should match original
            assertThat(decrypted.plaintext().asByteArray())
                    .isEqualTo(dataKey.plaintext().asByteArray());
        }

        @Test
        @DisplayName("should encrypt and decrypt arbitrary data via KMS")
        void shouldEncryptDecryptData() {
            // given
            byte[] plaintext = "Sensitive FSAMP metadata for FedRAMP SC-28".getBytes(StandardCharsets.UTF_8);

            // when - encrypt
            EncryptResponse encrypted = kmsClient.encrypt(EncryptRequest.builder()
                    .keyId(kmsKeyId)
                    .plaintext(SdkBytes.fromByteArray(plaintext))
                    .build());

            // then - ciphertext should differ from plaintext
            assertThat(encrypted.ciphertextBlob().asByteArray())
                    .isNotEqualTo(plaintext);

            // when - decrypt
            DecryptResponse decrypted = kmsClient.decrypt(DecryptRequest.builder()
                    .keyId(kmsKeyId)
                    .ciphertextBlob(encrypted.ciphertextBlob())
                    .build());

            // then - decrypted should match original
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
            // given
            String key = "sse-test/" + UUID.randomUUID() + "/encrypted-file.txt";
            byte[] content = "SSE-KMS encrypted content for FedRAMP SC-28".getBytes(StandardCharsets.UTF_8);

            // when - upload with explicit SSE-KMS
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

            // then - verify SSE headers on the stored object
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
            // when
            GetBucketEncryptionResponse encryption = s3Client.getBucketEncryption(
                    GetBucketEncryptionRequest.builder()
                            .bucket(TEST_BUCKET)
                            .build());

            // then
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
            // when
            GetBucketVersioningResponse versioning = s3Client.getBucketVersioning(
                    GetBucketVersioningRequest.builder()
                            .bucket(TEST_BUCKET)
                            .build());

            // then
            assertThat(versioning.status()).isEqualTo(BucketVersioningStatus.ENABLED);
        }
    }
}
