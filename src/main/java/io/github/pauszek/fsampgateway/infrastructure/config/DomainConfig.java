package io.github.pauszek.fsampgateway.infrastructure.config;

import io.github.pauszek.fsampgateway.adapter.out.crypto.TikaContentValidatorAdapter;
import io.github.pauszek.fsampgateway.adapter.out.messaging.SnsEventPublisherAdapter;
import io.github.pauszek.fsampgateway.adapter.out.persistence.InMemoryFileRepositoryAdapter;
import io.github.pauszek.fsampgateway.adapter.out.storage.S3StorageAdapter;
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

    /**
     * Secondary adapter bindings - these would be @Primary if multiple implementations exist.
     */
    
    @Bean
    public FileStoragePort fileStoragePort(S3StorageAdapter s3StorageAdapter) {
        return s3StorageAdapter;
    }

    @Bean
    public EventPublisherPort eventPublisherPort(SnsEventPublisherAdapter snsEventPublisherAdapter) {
        return snsEventPublisherAdapter;
    }

    @Bean
    public FileRepositoryPort fileRepositoryPort(InMemoryFileRepositoryAdapter inMemoryFileRepositoryAdapter) {
        return inMemoryFileRepositoryAdapter;
    }

    @Bean
    public ContentValidatorPort contentValidatorPort(TikaContentValidatorAdapter tikaContentValidatorAdapter) {
        return tikaContentValidatorAdapter;
    }
}
