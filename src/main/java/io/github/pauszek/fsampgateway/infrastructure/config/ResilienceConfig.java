package io.github.pauszek.fsampgateway.infrastructure.config;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.io.IOException;
import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(80.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .minimumNumberOfCalls(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(
                        S3Exception.class,
                        SnsException.class,
                        IOException.class
                )
                .build();

        CircuitBreakerConfig s3Config = CircuitBreakerConfig.from(defaultConfig)
                .slowCallDurationThreshold(Duration.ofSeconds(30)) // Large files take longer
                .build();

        CircuitBreakerConfig snsConfig = CircuitBreakerConfig.from(defaultConfig)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);
        registry.addConfiguration("s3", s3Config);
        registry.addConfiguration("sns", snsConfig);
        return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(
                        S3Exception.class,
                        SnsException.class,
                        IOException.class
                )
                .ignoreExceptions(IllegalArgumentException.class)
                .build();

        RetryConfig s3Config = RetryConfig.from(defaultConfig)
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .build();

        RetryConfig snsConfig = RetryConfig.from(defaultConfig)
                .maxAttempts(5)
                .waitDuration(Duration.ofMillis(200))
                .build();

        RetryRegistry registry = RetryRegistry.of(defaultConfig);
        registry.addConfiguration("s3", s3Config);
        registry.addConfiguration("sns", snsConfig);
        return registry;
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig uploadConfig = RateLimiterConfig.custom()
                .limitForPeriod(10)                          // 10 requests
                .limitRefreshPeriod(Duration.ofSeconds(1))   // per second
                .timeoutDuration(Duration.ofMillis(500))     // wait max 500ms if limit exceeded
                .build();

        RateLimiterConfig downloadConfig = RateLimiterConfig.custom()
                .limitForPeriod(50)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(100))
                .build();

        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)              // fail immediately
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(defaultConfig);
        registry.addConfiguration("upload", uploadConfig);
        registry.addConfiguration("download", downloadConfig);
        return registry;
    }

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        TimeLimiterConfig s3Config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30))
                .cancelRunningFuture(true)
                .build();

        TimeLimiterConfig snsConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .cancelRunningFuture(true)
                .build();

        TimeLimiterConfig defaultConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(15))
                .cancelRunningFuture(true)
                .build();

        TimeLimiterRegistry registry = TimeLimiterRegistry.of(defaultConfig);
        registry.addConfiguration("s3", s3Config);
        registry.addConfiguration("sns", snsConfig);
        return registry;
    }

    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        BulkheadConfig uploadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(25)
                .maxWaitDuration(Duration.ZERO)  // fail immediately if bulkhead full
                .build();

        BulkheadConfig defaultConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(50)
                .maxWaitDuration(Duration.ofMillis(100))
                .build();

        BulkheadRegistry registry = BulkheadRegistry.of(defaultConfig);
        registry.addConfiguration("upload", uploadConfig);
        return registry;
    }
}
