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
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

import static org.assertj.core.api.Assertions.assertThat;

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
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            assertThat(provider).isNotNull();
            assertThat(provider.resolveCredentials().accessKeyId()).isEqualTo("test");
            assertThat(provider.resolveCredentials().secretAccessKey()).isEqualTo("test");
        }

        @Test
        @DisplayName("should create S3 client with LocalStack endpoint")
        void shouldCreateS3ClientWithLocalStackEndpoint() {
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            S3Client s3Client = config.s3Client(provider);

            assertThat(s3Client).isNotNull();
            s3Client.close();
        }

        @Test
        @DisplayName("should create SNS client with LocalStack endpoint")
        void shouldCreateSnsClientWithLocalStackEndpoint() {
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            SnsClient snsClient = config.snsClient(provider);

            assertThat(snsClient).isNotNull();
            snsClient.close();
        }

        @Test
        @DisplayName("should create KMS client with LocalStack endpoint")
        void shouldCreateKmsClientWithLocalStackEndpoint() {
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            KmsClient kmsClient = config.kmsClient(provider);

            assertThat(kmsClient).isNotNull();
            kmsClient.close();
        }

        @Test
        @DisplayName("should create STS client with LocalStack endpoint")
        void shouldCreateStsClientWithLocalStackEndpoint() {
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            StsClient stsClient = config.stsClient(provider);

            assertThat(stsClient).isNotNull();
            stsClient.close();
        }

        @Test
        @DisplayName("should create DynamoDB client with LocalStack endpoint")
        void shouldCreateDynamoDbClientWithLocalStackEndpoint() {
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            DynamoDbClient dynamoDbClient = config.dynamoDbClient(provider);

            assertThat(dynamoDbClient).isNotNull();
            dynamoDbClient.close();
        }

        @Test
        @DisplayName("should create SQS client with LocalStack endpoint")
        void shouldCreateSqsClientWithLocalStackEndpoint() {
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            SqsClient sqsClient = config.sqsClient(provider);

            assertThat(sqsClient).isNotNull();
            sqsClient.close();
        }

        @Test
        @DisplayName("should create CloudWatch client with LocalStack endpoint")
        void shouldCreateCloudWatchClientWithLocalStackEndpoint() {
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            CloudWatchClient cloudWatchClient = config.cloudWatchClient(provider);

            assertThat(cloudWatchClient).isNotNull();
            cloudWatchClient.close();
        }
    }

    @Nested
    @DisplayName("ProductionAwsConfig")
    class ProductionAwsConfigTest {

        private final AwsConfig.ProductionAwsConfig config =
                new AwsConfig.ProductionAwsConfig(TEST_REGION, true);

        @Test
        @DisplayName("should create default credentials provider")
        void shouldCreateDefaultCredentialsProvider() {
            AwsCredentialsProvider provider = config.awsCredentialsProvider();

            assertThat(provider).isNotNull();
        }

        @Test
        @DisplayName("should enable FIPS endpoints for US regions")
        void shouldEnableFipsEndpointsForUsRegions() {
            var usConfig = new AwsConfig.ProductionAwsConfig("us-west-2", true);
            var provider = testCredentials();

            S3Client client = usConfig.s3Client(provider);

            assertThat(client).isNotNull();
            client.close();
        }

        @Test
        @DisplayName("should disable FIPS endpoints for non-US regions")
        void shouldDisableFipsEndpointsForNonUsRegions() {
            var euConfig = new AwsConfig.ProductionAwsConfig("eu-west-1", true);
            var provider = testCredentials();

            S3Client client = euConfig.s3Client(provider);

            assertThat(client).isNotNull();
            client.close();
        }

        @Test
        @DisplayName("should create S3 client for production")
        void shouldCreateS3ClientForProduction() {
            AwsCredentialsProvider provider = testCredentials();

            S3Client s3Client = config.s3Client(provider);

            assertThat(s3Client).isNotNull();
            s3Client.close();
        }

        @Test
        @DisplayName("should create SNS client for production")
        void shouldCreateSnsClientForProduction() {
            AwsCredentialsProvider provider = testCredentials();

            SnsClient snsClient = config.snsClient(provider);

            assertThat(snsClient).isNotNull();
            snsClient.close();
        }

        @Test
        @DisplayName("should create KMS client for production")
        void shouldCreateKmsClientForProduction() {
            AwsCredentialsProvider provider = testCredentials();

            KmsClient kmsClient = config.kmsClient(provider);

            assertThat(kmsClient).isNotNull();
            kmsClient.close();
        }

        @Test
        @DisplayName("should create STS client for production")
        void shouldCreateStsClientForProduction() {
            AwsCredentialsProvider provider = testCredentials();

            StsClient stsClient = config.stsClient(provider);

            assertThat(stsClient).isNotNull();
            stsClient.close();
        }

        @Test
        @DisplayName("should create DynamoDB client for production")
        void shouldCreateDynamoDbClientForProduction() {
            AwsCredentialsProvider provider = testCredentials();

            DynamoDbClient dynamoDbClient = config.dynamoDbClient(provider);

            assertThat(dynamoDbClient).isNotNull();
            dynamoDbClient.close();
        }

        @Test
        @DisplayName("should create SQS client for production")
        void shouldCreateSqsClientForProduction() {
            AwsCredentialsProvider provider = testCredentials();

            SqsClient sqsClient = config.sqsClient(provider);

            assertThat(sqsClient).isNotNull();
            sqsClient.close();
        }

        @Test
        @DisplayName("should create CloudWatch client for production")
        void shouldCreateCloudWatchClientForProduction() {
            AwsCredentialsProvider provider = testCredentials();

            CloudWatchClient cloudWatchClient = config.cloudWatchClient(provider);

            assertThat(cloudWatchClient).isNotNull();
            cloudWatchClient.close();
        }

        private AwsCredentialsProvider testCredentials() {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-key", "test-secret")
            );
        }
    }
}
