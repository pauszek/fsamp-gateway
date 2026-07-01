package io.github.pauszek.fsampgateway.infrastructure.security;

import io.github.pauszek.fsampgateway.adapter.out.storage.S3StorageProperties;
import io.github.pauszek.fsampgateway.application.mapper.FileMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;

import static org.mockito.Mockito.mock;

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
    public FileMapper mockFileMapper() {
        return mock(FileMapper.class);
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
