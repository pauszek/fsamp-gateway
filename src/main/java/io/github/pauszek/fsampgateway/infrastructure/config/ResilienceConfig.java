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

/**
 * Resilience4j Configuration.
 * 
 * Configures Circuit Breakers and Retry policies for AWS integrations.
 * 
 * Pattern rationale:
 * - Circuit Breaker: Prevents cascade failures when AWS services are unavailable
 * - Retry: Handles transient failures (network timeouts, throttling)
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        // Common configuration for AWS service circuit breakers
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

        // S3 specific configuration - more tolerant for large file uploads
        CircuitBreakerConfig s3Config = CircuitBreakerConfig.from(defaultConfig)
                .slowCallDurationThreshold(Duration.ofSeconds(30)) // Large files take longer
                .build();

        // SNS configuration - faster timeouts
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
        // Default retry configuration
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

        // S3 retry - fewer attempts, longer waits
        RetryConfig s3Config = RetryConfig.from(defaultConfig)
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .build();

        // SNS retry - more attempts, shorter waits
        RetryConfig snsConfig = RetryConfig.from(defaultConfig)
                .maxAttempts(5)
                .waitDuration(Duration.ofMillis(200))
                .build();

        RetryRegistry registry = RetryRegistry.of(defaultConfig);
        registry.addConfiguration("s3", s3Config);
        registry.addConfiguration("sns", snsConfig);
        return registry;
    }

    /**
     * Rate Limiter Registry.
     * 
     * Protects against:
     * - DoS attacks
     * - Excessive API usage
     * - Cost overruns from high AWS API calls
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        // File upload - stricter limits (resource intensive)
        RateLimiterConfig uploadConfig = RateLimiterConfig.custom()
                .limitForPeriod(10)                          // 10 requests
                .limitRefreshPeriod(Duration.ofSeconds(1))   // per second
                .timeoutDuration(Duration.ofMillis(500))     // wait max 500ms if limit exceeded
                .build();

        // File download - higher limits
        RateLimiterConfig downloadConfig = RateLimiterConfig.custom()
                .limitForPeriod(50)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(100))
                .build();

        // Default - general API limits
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

    /**
     * Time Limiter Registry.
     * 
     * Prevents long-running operations from blocking threads indefinitely.
     * Critical for async operations and CompletableFuture handling.
     */
    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        // S3 operations - longer timeout for large files
        TimeLimiterConfig s3Config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30))
                .cancelRunningFuture(true)
                .build();

        // SNS operations - shorter timeout
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

    /**
     * Bulkhead Registry.
     * 
     * Limits concurrent calls to prevent resource exhaustion.
     * Implements the Bulkhead pattern for fault isolation.
     */
    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        // File upload - limited concurrent uploads
        BulkheadConfig uploadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(25)
                .maxWaitDuration(Duration.ZERO)  // fail immediately if bulkhead full
                .build();

        // Default
        BulkheadConfig defaultConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(50)
                .maxWaitDuration(Duration.ofMillis(100))
                .build();

        BulkheadRegistry registry = BulkheadRegistry.of(defaultConfig);
        registry.addConfiguration("upload", uploadConfig);
        return registry;
    }
}
