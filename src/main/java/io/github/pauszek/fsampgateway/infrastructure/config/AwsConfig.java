package io.github.pauszek.fsampgateway.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

import java.net.URI;

/**
 * AWS SDK Configuration.
 * 
 * Configures AWS clients for S3, SNS, SQS, KMS, DynamoDB, STS.
 * Supports LocalStack for local development.
 * 
 * FIPS 140-3-oriented posture:
 * - Production clients use FIPS endpoints (.fipsEnabled(true))
 * - FIPS endpoints route traffic to FIPS-validated TLS termination points
 * - Combined with ACCP FIPS provider for a FIPS-oriented communication path
 * 
 * @see <a href="https://aws.amazon.com/compliance/fips/">AWS FIPS Endpoints</a>
 */
@Configuration
public class AwsConfig {

    private static final Logger log = LoggerFactory.getLogger(AwsConfig.class);

    @Value("${aws.region:us-west-2}")
    private String region;

    /**
     * Production AWS configuration using default credentials chain.
     * All clients use FIPS endpoints for the FIPS 140-3-oriented posture.
     */
    @Configuration
    @Profile("!local")
    static class ProductionAwsConfig {

        private final String region;
        private final boolean useFipsEndpoints;

        ProductionAwsConfig(
                @Value("${aws.region:us-west-2}") String region,
                @Value("${aws.fips-endpoints:true}") boolean useFipsEndpoints) {
            this.region = region;
            this.useFipsEndpoints = useFipsEndpoints && region.startsWith("us-");
        }

        @Bean
        public AwsCredentialsProvider awsCredentialsProvider() {
            log.info("Using AWS default credentials provider chain");
            return DefaultCredentialsProvider.create();
        }

        @Bean
        public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating S3 client for region: {} (FIPS: {})", region, useFipsEndpoints);
            return S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentialsProvider)
                    .fipsEnabled(useFipsEndpoints)
                    .build();
        }

        @Bean
        public SnsClient snsClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating SNS client for region: {} (FIPS: {})", region, useFipsEndpoints);
            return SnsClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentialsProvider)
                    .fipsEnabled(useFipsEndpoints)
                    .build();
        }

        @Bean
        public KmsClient kmsClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating KMS client for region: {} (FIPS: {})", region, useFipsEndpoints);
            return KmsClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentialsProvider)
                    .fipsEnabled(useFipsEndpoints)
                    .build();
        }

        @Bean
        public StsClient stsClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating STS client for region: {} (FIPS: {})", region, useFipsEndpoints);
            return StsClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentialsProvider)
                    .fipsEnabled(useFipsEndpoints)
                    .build();
        }
        
        @Bean
        public DynamoDbClient dynamoDbClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating DynamoDB client for region: {} (FIPS: {})", region, useFipsEndpoints);
            return DynamoDbClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentialsProvider)
                    .fipsEnabled(useFipsEndpoints)
                    .build();
        }

        @Bean
        public SqsClient sqsClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating SQS client for region: {} (FIPS: {})", region, useFipsEndpoints);
            return SqsClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentialsProvider)
                    .fipsEnabled(useFipsEndpoints)
                    .build();
        }

        @Bean
        public CloudWatchClient cloudWatchClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating CloudWatch client for region: {} (FIPS: {})", region, useFipsEndpoints);
            return CloudWatchClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentialsProvider)
                    .fipsEnabled(useFipsEndpoints)
                    .build();
        }
    }

    /**
     * LocalStack configuration for local development.
     */
    @Configuration
    @Profile("local")
    static class LocalStackAwsConfig {

        private final String localstackUrl;
        private final String region;

        LocalStackAwsConfig(
                @Value("${AWS_ENDPOINT_URL:${aws.endpoint:http://localhost:4566}}") String localstackUrl,
                @Value("${AWS_REGION:${aws.region:us-west-2}}") String region) {
            this.localstackUrl = localstackUrl;
            this.region = region;
        }

        @Bean
        public AwsCredentialsProvider awsCredentialsProvider() {
            log.info("Using LocalStack static credentials");
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test", "test")
            );
        }

        @Bean
        public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating S3 client for LocalStack: {}", localstackUrl);
            return S3Client.builder()
                    .region(Region.of(region))
                    .endpointOverride(URI.create(localstackUrl))
                    .credentialsProvider(credentialsProvider)
                    .forcePathStyle(true) // Required for LocalStack
                    .build();
        }

        @Bean
        public SnsClient snsClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating SNS client for LocalStack: {}", localstackUrl);
            return SnsClient.builder()
                    .region(Region.of(region))
                    .endpointOverride(URI.create(localstackUrl))
                    .credentialsProvider(credentialsProvider)
                    .build();
        }

        @Bean
        public KmsClient kmsClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating KMS client for LocalStack: {}", localstackUrl);
            return KmsClient.builder()
                    .region(Region.of(region))
                    .endpointOverride(URI.create(localstackUrl))
                    .credentialsProvider(credentialsProvider)
                    .build();
        }

        @Bean
        public StsClient stsClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating STS client for LocalStack: {}", localstackUrl);
            return StsClient.builder()
                    .region(Region.of(region))
                    .endpointOverride(URI.create(localstackUrl))
                    .credentialsProvider(credentialsProvider)
                    .build();
        }
        
        @Bean
        public DynamoDbClient dynamoDbClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating DynamoDB client for LocalStack: {}", localstackUrl);
            return DynamoDbClient.builder()
                    .region(Region.of(region))
                    .endpointOverride(URI.create(localstackUrl))
                    .credentialsProvider(credentialsProvider)
                    .build();
        }

        @Bean
        public SqsClient sqsClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating SQS client for LocalStack: {}", localstackUrl);
            return SqsClient.builder()
                    .region(Region.of(region))
                    .endpointOverride(URI.create(localstackUrl))
                    .credentialsProvider(credentialsProvider)
                    .build();
        }

        @Bean
        public CloudWatchClient cloudWatchClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating CloudWatch client for LocalStack: {}", localstackUrl);
            return CloudWatchClient.builder()
                    .region(Region.of(region))
                    .endpointOverride(URI.create(localstackUrl))
                    .credentialsProvider(credentialsProvider)
                    .build();
        }
    }
}
