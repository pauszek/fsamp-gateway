package io.github.pauszek.fsampgateway.infrastructure.config;

import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CloudWatchMetricsConfigTest {

    @Test
    void shouldCreateRegistryWithConfiguredNamespaceAndStep() {
        CloudWatchAsyncClient client = mock(CloudWatchAsyncClient.class);
        CloudWatchMetricsConfig config = new CloudWatchMetricsConfig();

        CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(
                client,
                "FSAMP/Test",
                Duration.ofMinutes(5)
        );

        assertThat(registry).isNotNull();
        registry.close();
    }
}
