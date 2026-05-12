package io.github.pauszek.fsampgateway.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateAliasRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.*;

/**
 * Base class for integration tests using LocalStack.
 * 
 * Provides:
 * - LocalStack container with AWS services
 * - Pre-created S3 bucket, SNS topic, SQS queue, DynamoDB table
 * - Spring Boot test context with "local" profile (reuses LocalStack config)
 * 
 * All integration tests should extend this class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"local", "integration-test"})
@Tag("integration")
public abstract class BaseIntegrationTest {

    protected static final String TEST_BUCKET = "fsamp-files-integration";
    protected static final String TEST_TOPIC_NAME = "fsamp-file-events-integration";
    protected static final String TEST_QUEUE_NAME = "fsamp-file-events-queue-integration";
    protected static final String TEST_IDEMPOTENCY_TABLE = "fsamp-idempotency-keys";
    protected static final String TEST_KMS_ALIAS = "alias/fsamp-integration-key";

    private static final DockerImageName LOCALSTACK_IMAGE = 
            DockerImageName.parse("localstack/localstack-pro:4.14.0");

    /**
     * Shared LocalStack Pro container - started once and reused across all integration tests.
     * 
     * Uses Pro edition for full AWS parity: KMS, IAM enforcement, CloudWatch.
     * LOCALSTACK_AUTH_TOKEN must be set as environment variable.
     */
    protected static final LocalStackContainer LOCALSTACK;

    /** KMS key ID created during container init — available for subclass tests. */
    protected static String kmsKeyId;

    static {
        LOCALSTACK = new LocalStackContainer(LOCALSTACK_IMAGE)
                .withServices(S3, SNS, SQS, DYNAMODB, KMS, IAM, STS, CLOUDWATCH)
                .withEnv("LOCALSTACK_AUTH_TOKEN", System.getenv("LOCALSTACK_AUTH_TOKEN"))
                .withEnv("ENFORCE_IAM", "1")
                .withEnv("IAM_SOFT_MODE", "0");
        LOCALSTACK.start();

        // Create KMS key once for all integration tests
        initKmsKey();
    }

    /**
     * Initialise a shared KMS key in LocalStack for SSE-KMS encryption tests.
     */
    private static void initKmsKey() {
        KmsClient kms = KmsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(software.amazon.awssdk.regions.Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")))
                .build();

        CreateKeyResponse key = kms.createKey(CreateKeyRequest.builder()
                .description("FSAMP integration test key")
                .build());
        kmsKeyId = key.keyMetadata().keyId();

        kms.createAlias(CreateAliasRequest.builder()
                .aliasName(TEST_KMS_ALIAS)
                .targetKeyId(kmsKeyId)
                .build());
        kms.close();
    }

    /**
     * Dynamically configure Spring properties to use LocalStack endpoints.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String endpoint = LOCALSTACK.getEndpoint().toString();
        registry.add("aws.endpoint", () -> endpoint);
        registry.add("AWS_ENDPOINT_URL", () -> endpoint);
        registry.add("aws.region", LOCALSTACK::getRegion);
        registry.add("AWS_REGION", LOCALSTACK::getRegion);
        registry.add("aws.kms.key-id", () -> kmsKeyId);
        registry.add("KMS_KEY_ID", () -> kmsKeyId);
        registry.add("aws.dynamodb.idempotency-table-name", () -> TEST_IDEMPOTENCY_TABLE);
    }

    @Autowired
    protected S3Client s3Client;

    @Autowired
    protected SnsClient snsClient;

    @Autowired
    protected SqsClient sqsClient;

    @Autowired
    protected DynamoDbClient dynamoDbClient;

    protected String topicArn;
    protected String queueUrl;

    @BeforeEach
    void setUpAwsResources() {
        createBucketIfNotExists();
        topicArn = createTopicIfNotExists();
        queueUrl = createQueueIfNotExists();
        createIdempotencyTableIfNotExists();
    }

    private void createBucketIfNotExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(TEST_BUCKET).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build());

            // Enable SSE-KMS encryption (mirrors production / init-aws.sh)
            s3Client.putBucketEncryption(PutBucketEncryptionRequest.builder()
                    .bucket(TEST_BUCKET)
                    .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                            .rules(ServerSideEncryptionRule.builder()
                                    .applyServerSideEncryptionByDefault(
                                            ServerSideEncryptionByDefault.builder()
                                                    .sseAlgorithm(ServerSideEncryption.AWS_KMS)
                                                    .kmsMasterKeyID(kmsKeyId)
                                                    .build())
                                    .bucketKeyEnabled(true)
                                    .build())
                            .build())
                    .build());

            // Enable versioning
            s3Client.putBucketVersioning(PutBucketVersioningRequest.builder()
                    .bucket(TEST_BUCKET)
                    .versioningConfiguration(VersioningConfiguration.builder()
                            .status(BucketVersioningStatus.ENABLED)
                            .build())
                    .build());
        }
    }

    private String createTopicIfNotExists() {
        // CreateTopic is idempotent - returns existing topic ARN if it exists
        return snsClient.createTopic(CreateTopicRequest.builder()
                .name(TEST_TOPIC_NAME)
                .build()).topicArn();
    }

    private String createQueueIfNotExists() {
        // CreateQueue is idempotent
        return sqsClient.createQueue(CreateQueueRequest.builder()
                .queueName(TEST_QUEUE_NAME)
                .build()).queueUrl();
    }

    private void createIdempotencyTableIfNotExists() {
        try {
            dynamoDbClient.describeTable(DescribeTableRequest.builder()
                    .tableName(TEST_IDEMPOTENCY_TABLE)
                    .build());
        } catch (ResourceNotFoundException e) {
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(TEST_IDEMPOTENCY_TABLE)
                    .keySchema(
                            KeySchemaElement.builder()
                                    .attributeName("idempotencyKey")
                                    .keyType(KeyType.HASH)
                                    .build(),
                            KeySchemaElement.builder()
                                    .attributeName("userId")
                                    .keyType(KeyType.RANGE)
                                    .build()
                    )
                    .attributeDefinitions(
                            AttributeDefinition.builder()
                                    .attributeName("idempotencyKey")
                                    .attributeType(ScalarAttributeType.S)
                                    .build(),
                            AttributeDefinition.builder()
                                    .attributeName("userId")
                                    .attributeType(ScalarAttributeType.S)
                                    .build()
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());

            // Wait for table to be active
            dynamoDbClient.waiter().waitUntilTableExists(
                    DescribeTableRequest.builder().tableName(TEST_IDEMPOTENCY_TABLE).build()
            );
        }
    }
}
