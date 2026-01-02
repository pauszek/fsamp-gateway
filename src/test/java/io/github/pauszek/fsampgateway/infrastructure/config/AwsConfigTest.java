package io.github.pauszek.fsampgateway.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sts.StsClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AwsConfig.
 */
@DisplayName("AwsConfig")
class AwsConfigTest {

    private static final String TEST_REGION = "us-west-2";
    private static final String LOCALSTACK_URL = "http://localhost:4566";

    @Nested
    @DisplayName("LocalStackAwsConfig")
    class LocalStackAwsConfigTest {

        private final AwsConfig.LocalStackAwsConfig config =
                new AwsConfig.LocalStackAwsConfig(LOCALSTACK_URL, TEST_REGION);

        @Test
        @DisplayName("should create static credentials provider for LocalStack")
        void shouldCreateStaticCredentialsProvider() {
            // when
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            // then
            assertThat(provider).isNotNull();
            assertThat(provider.resolveCredentials().accessKeyId()).isEqualTo("test");
            assertThat(provider.resolveCredentials().secretAccessKey()).isEqualTo("test");
        }

        @Test
        @DisplayName("should create S3 client with LocalStack endpoint")
        void shouldCreateS3ClientWithLocalStackEndpoint() {
            // given
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            // when
            S3Client s3Client = config.s3Client(provider);

            // then
            assertThat(s3Client).isNotNull();
            s3Client.close();
        }

        @Test
        @DisplayName("should create SNS client with LocalStack endpoint")
        void shouldCreateSnsClientWithLocalStackEndpoint() {
            // given
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            // when
            SnsClient snsClient = config.snsClient(provider);

            // then
            assertThat(snsClient).isNotNull();
            snsClient.close();
        }

        @Test
        @DisplayName("should create KMS client with LocalStack endpoint")
        void shouldCreateKmsClientWithLocalStackEndpoint() {
            // given
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            // when
            KmsClient kmsClient = config.kmsClient(provider);

            // then
            assertThat(kmsClient).isNotNull();
            kmsClient.close();
        }

        @Test
        @DisplayName("should create STS client with LocalStack endpoint")
        void shouldCreateStsClientWithLocalStackEndpoint() {
            // given
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            // when
            StsClient stsClient = config.stsClient(provider);

            // then
            assertThat(stsClient).isNotNull();
            stsClient.close();
        }

        @Test
        @DisplayName("should create DynamoDB client with LocalStack endpoint")
        void shouldCreateDynamoDbClientWithLocalStackEndpoint() {
            // given
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            // when
            DynamoDbClient dynamoDbClient = config.dynamoDbClient(provider);

            // then
            assertThat(dynamoDbClient).isNotNull();
            dynamoDbClient.close();
        }

        @Test
        @DisplayName("should create SQS client with LocalStack endpoint")
        void shouldCreateSqsClientWithLocalStackEndpoint() {
            // given
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            // when
            SqsClient sqsClient = config.sqsClient(provider);

            // then
            assertThat(sqsClient).isNotNull();
            sqsClient.close();
        }
    }

    @Nested
    @DisplayName("ProductionAwsConfig")
    class ProductionAwsConfigTest {

        private final AwsConfig.ProductionAwsConfig config =
                new AwsConfig.ProductionAwsConfig(TEST_REGION);

        @Test
        @DisplayName("should create default credentials provider")
        void shouldCreateDefaultCredentialsProvider() {
            // when
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            // then
            assertThat(provider).isNotNull();
        }

        @Test
        @DisplayName("should create S3 client for production")
        void shouldCreateS3ClientForProduction() {
            // given - use static creds for test (avoid real AWS calls)
            AwsCredentialsProvider provider = testCredentials();

            // when
            S3Client s3Client = config.s3Client(provider);

            // then
            assertThat(s3Client).isNotNull();
            s3Client.close();
        }

        @Test
        @DisplayName("should create SNS client for production")
        void shouldCreateSnsClientForProduction() {
            // given
            AwsCredentialsProvider provider = testCredentials();

            // when
            SnsClient snsClient = config.snsClient(provider);

            // then
            assertThat(snsClient).isNotNull();
            snsClient.close();
        }

        @Test
        @DisplayName("should create KMS client for production")
        void shouldCreateKmsClientForProduction() {
            // given
            AwsCredentialsProvider provider = testCredentials();

            // when
            KmsClient kmsClient = config.kmsClient(provider);

            // then
            assertThat(kmsClient).isNotNull();
            kmsClient.close();
        }

        @Test
        @DisplayName("should create STS client for production")
        void shouldCreateStsClientForProduction() {
            // given
            AwsCredentialsProvider provider = testCredentials();

            // when
            StsClient stsClient = config.stsClient(provider);

            // then
            assertThat(stsClient).isNotNull();
            stsClient.close();
        }

        @Test
        @DisplayName("should create DynamoDB client for production")
        void shouldCreateDynamoDbClientForProduction() {
            // given
            AwsCredentialsProvider provider = testCredentials();

            // when
            DynamoDbClient dynamoDbClient = config.dynamoDbClient(provider);

            // then
            assertThat(dynamoDbClient).isNotNull();
            dynamoDbClient.close();
        }

        @Test
        @DisplayName("should create SQS client for production")
        void shouldCreateSqsClientForProduction() {
            // given
            AwsCredentialsProvider provider = testCredentials();

            // when
            SqsClient sqsClient = config.sqsClient(provider);

            // then
            assertThat(sqsClient).isNotNull();
            sqsClient.close();
        }

        private AwsCredentialsProvider testCredentials() {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-key", "test-secret")
            );
        }
    }
}
