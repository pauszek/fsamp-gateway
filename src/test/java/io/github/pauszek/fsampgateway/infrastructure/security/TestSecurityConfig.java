package io.github.pauszek.fsampgateway.infrastructure.security;

import io.github.pauszek.fsampgateway.adapter.out.storage.S3StorageProperties;
import io.github.pauszek.fsampgateway.application.mapper.FileMapper;
import io.github.pauszek.fsampgateway.domain.port.in.UploadFileUseCase;
import io.github.pauszek.fsampgateway.domain.port.out.ContentValidatorPort;
import io.github.pauszek.fsampgateway.domain.port.out.EventPublisherPort;
import io.github.pauszek.fsampgateway.domain.port.out.FileRepositoryPort;
import io.github.pauszek.fsampgateway.domain.port.out.FileStoragePort;
import io.github.pauszek.fsampgateway.infrastructure.security.cognito.CurrentUserService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;

import static org.mockito.Mockito.mock;

/**
 * Test configuration that provides mock beans for security integration tests.
 * This allows SecurityIntegrationTest to run without full AWS infrastructure.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public S3Client mockS3Client() {
        return mock(S3Client.class);
    }

    @Bean
    @Primary
    public SnsClient mockSnsClient() {
        return mock(SnsClient.class);
    }

    @Bean
    @Primary
    public S3StorageProperties testS3Properties() {
        S3StorageProperties props = new S3StorageProperties();
        props.setBucketName("test-bucket");
        props.setKmsKeyId("alias/test-key");
        return props;
    }
}
