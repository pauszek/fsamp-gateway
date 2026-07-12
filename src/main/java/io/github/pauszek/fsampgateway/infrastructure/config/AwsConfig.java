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
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.net.URI;

@Configuration
public class AwsConfig {

    private static final Logger log = LoggerFactory.getLogger(AwsConfig.class);

    @Value("${aws.region:us-west-2}")
    private String region;

    @Configuration
    @Profile("!local")
    static class ProductionAwsConfig {

        private static final String SUPPORTED_FIPS_ENDPOINT_REGION = "us-west-2";

        private final String region;
        private final boolean useFipsEndpoints;

        ProductionAwsConfig(
                @Value("${aws.region:us-west-2}") String region,
                @Value("${aws.fips-endpoints:true}") boolean useFipsEndpoints) {
            this.region = region;
            if (!isFipsEndpointRegion(region)) {
                throw new IllegalArgumentException(
                        "FSAMP active AWS deployments are pinned to the us-west-2 FIPS endpoint baseline: "
                                + region);
            }
            if (!useFipsEndpoints) {
                throw new IllegalArgumentException(
                        "AWS FIPS endpoints must be enabled outside the local profile");
            }
            this.useFipsEndpoints = useFipsEndpoints;
        }

        private static boolean isFipsEndpointRegion(String region) {
            return SUPPORTED_FIPS_ENDPOINT_REGION.equals(region);
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

        @Bean
        public CloudWatchAsyncClient cloudWatchAsyncClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating async CloudWatch client for region: {} (FIPS: {})", region, useFipsEndpoints);
            return CloudWatchAsyncClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentialsProvider)
                    .fipsEnabled(useFipsEndpoints)
                    .build();
        }
    }

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

        @Bean
        public CloudWatchAsyncClient cloudWatchAsyncClient(AwsCredentialsProvider credentialsProvider) {
            log.info("Creating async CloudWatch client for LocalStack: {}", localstackUrl);
            return CloudWatchAsyncClient.builder()
                    .region(Region.of(region))
                    .endpointOverride(URI.create(localstackUrl))
                    .credentialsProvider(credentialsProvider)
                    .build();
        }
    }
}
