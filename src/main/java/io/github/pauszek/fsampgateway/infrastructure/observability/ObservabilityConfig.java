package io.github.pauszek.fsampgateway.infrastructure.observability;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags(Tags.of(
                Tag.of("application", "fsamp-gateway"),
                Tag.of("environment", getEnvironment())
        ));
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    private String getEnvironment() {
        String env = System.getenv("SPRING_PROFILES_ACTIVE");
        return env != null ? env : "local";
    }
}
