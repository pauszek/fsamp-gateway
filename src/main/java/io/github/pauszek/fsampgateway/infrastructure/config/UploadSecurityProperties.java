package io.github.pauszek.fsampgateway.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "fsamp.security")
public record UploadSecurityProperties(
        boolean fipsMode,
        @NotEmpty Set<String> allowedContentTypes,
        @Min(1) long maxFileSizeBytes
) {
    public UploadSecurityProperties {
        allowedContentTypes = allowedContentTypes == null
                ? Set.of()
                : allowedContentTypes.stream()
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean isContentTypeAllowed(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType.toLowerCase());
    }
}
