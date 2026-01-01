package io.github.pauszek.fsampgateway.infrastructure.config;

import io.github.pauszek.fsampgateway.domain.port.out.ContentValidatorPort;
import io.github.pauszek.fsampgateway.domain.port.out.EventPublisherPort;
import io.github.pauszek.fsampgateway.domain.port.out.FileRepositoryPort;
import io.github.pauszek.fsampgateway.domain.port.out.FileStoragePort;
import io.github.pauszek.fsampgateway.domain.service.FileUploadDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Domain Layer Configuration.
 * 
 * Wires together domain services with their ports/adapters.
 * This is the composition root where dependency injection happens.
 */
@Configuration
public class DomainConfig {

    /**
     * Primary domain service for file upload orchestration.
     */
    @Bean
    public FileUploadDomainService fileUploadDomainService(
            FileStoragePort fileStorage,
            EventPublisherPort eventPublisher,
            FileRepositoryPort fileRepository,
            ContentValidatorPort contentValidator) {
        
        return new FileUploadDomainService(
                contentValidator,
                fileStorage,
                eventPublisher,
                fileRepository
        );
    }

    // Note: Adapter beans are registered via @Component annotations on the adapters themselves.
    // No additional @Bean definitions needed here for ports - Spring autowires by type.
}
