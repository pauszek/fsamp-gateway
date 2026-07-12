package io.github.pauszek.fsampgateway.infrastructure.config;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(
        prefix = "management.cloudwatch.metrics.export",
        name = "enabled",
        havingValue = "true"
)
public class CloudWatchMetricsConfig {

    @Bean
    public CloudWatchMeterRegistry cloudWatchMeterRegistry(
            CloudWatchAsyncClient cloudWatchClient,
            @Value("${management.cloudwatch.metrics.export.namespace:FSAMP/Gateway}") String namespace,
            @Value("${management.cloudwatch.metrics.export.step:1m}") Duration step
    ) {
        CloudWatchConfig config = new CloudWatchConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String namespace() {
                return namespace;
            }

            @Override
            public Duration step() {
                return step;
            }
        };
        return new CloudWatchMeterRegistry(config, Clock.SYSTEM, cloudWatchClient);
    }
}
