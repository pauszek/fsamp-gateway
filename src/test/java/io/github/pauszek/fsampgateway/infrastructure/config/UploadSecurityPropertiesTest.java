package io.github.pauszek.fsampgateway.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UploadSecurityPropertiesTest {

    @Test
    void shouldNormalizeConfiguredContentTypesAndMatchCaseInsensitively() {
        UploadSecurityProperties properties = new UploadSecurityProperties(
                true,
                Set.of(" Application/PDF ", "IMAGE/PNG"),
                1024
        );

        assertThat(properties.allowedContentTypes())
                .containsExactlyInAnyOrder("application/pdf", "image/png");
        assertThat(properties.isContentTypeAllowed("APPLICATION/PDF")).isTrue();
        assertThat(properties.isContentTypeAllowed("text/plain")).isFalse();
        assertThat(properties.isContentTypeAllowed(null)).isFalse();
    }

    @Test
    void shouldConvertNullContentTypeSetToEmptySet() {
        UploadSecurityProperties properties = new UploadSecurityProperties(false, null, 1);

        assertThat(properties.allowedContentTypes()).isEmpty();
    }
}
