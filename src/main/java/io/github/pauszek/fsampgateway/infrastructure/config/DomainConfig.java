package io.github.pauszek.fsampgateway.infrastructure.config;

import io.github.pauszek.fsampgateway.domain.port.out.ContentValidatorPort;
import io.github.pauszek.fsampgateway.domain.port.out.EventPublisherPort;
import io.github.pauszek.fsampgateway.domain.port.out.FileRepositoryPort;
import io.github.pauszek.fsampgateway.domain.port.out.FileStoragePort;
import io.github.pauszek.fsampgateway.domain.service.FileQueryDomainService;
import io.github.pauszek.fsampgateway.domain.service.FileUploadDomainService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public FileUploadDomainService fileUploadDomainService(
            FileStoragePort fileStorage,
            EventPublisherPort eventPublisher,
            FileRepositoryPort fileRepository,
            ContentValidatorPort contentValidator,
            UploadSecurityProperties uploadSecurityProperties,
            @Value("${aws.dynamodb.direct-publish-after-outbox:false}") boolean directPublishAfterOutbox) {

        return new FileUploadDomainService(
                contentValidator,
                fileStorage,
                eventPublisher,
                fileRepository,
                uploadSecurityProperties.allowedContentTypes(),
                uploadSecurityProperties.maxFileSizeBytes(),
                directPublishAfterOutbox
        );
    }

    @Bean
    public FileQueryDomainService fileQueryDomainService(
            FileRepositoryPort fileRepository,
            FileStoragePort fileStorage) {

        return new FileQueryDomainService(fileRepository, fileStorage);
    }

}
