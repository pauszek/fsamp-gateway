package io.github.pauszek.fsampgateway.adapter.in.web;

import io.github.pauszek.fsampgateway.application.dto.ApiErrorDto;
import io.github.pauszek.fsampgateway.domain.exception.*;
import io.github.pauszek.fsampgateway.infrastructure.idempotency.IdempotencyConflictException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        given(webRequest.getDescription(false)).willReturn("uri=/api/v1/files");
        MDC.put("correlationId", "test-corr-123");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    @DisplayName("Domain Exceptions")
    class DomainExceptions {

        @Test
        @DisplayName("should return 400 for FileValidationException")
        void shouldReturn400ForFileValidationException() {
            var ex = new FileValidationException("Invalid file content");

            ResponseEntity<ApiErrorDto> response = handler.handleFileValidation(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().error()).isEqualTo("FILE_VALIDATION_ERROR");
            assertThat(response.getBody().message()).isEqualTo("Invalid file content");
            assertThat(response.getBody().path()).isEqualTo("/api/v1/files");
            assertThat(response.getBody().correlationId()).isEqualTo("test-corr-123");
        }

        @Test
        @DisplayName("should return 404 for FileNotFoundException")
        void shouldReturn404ForFileNotFoundException() {
            var ex = new FileNotFoundException("File abc-123 not found");

            ResponseEntity<ApiErrorDto> response = handler.handleFileNotFound(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().error()).isEqualTo("FILE_NOT_FOUND");
            assertThat(response.getBody().message()).isEqualTo("File abc-123 not found");
        }

        @Test
        @DisplayName("should return 503 for StorageException")
        void shouldReturn503ForStorageException() {
            var ex = new StorageException("S3 connection failed");

            ResponseEntity<ApiErrorDto> response = handler.handleStorage(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().error()).isEqualTo("STORAGE_ERROR");
            assertThat(response.getBody().message()).isEqualTo("Storage service unavailable");
        }

        @Test
        @DisplayName("should return 503 for EventPublishException")
        void shouldReturn503ForEventPublishException() {
            var ex = new EventPublishException("SNS publish failed");

            ResponseEntity<ApiErrorDto> response = handler.handleEventPublish(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().error()).isEqualTo("EVENT_PUBLISH_ERROR");
            assertThat(response.getBody().message()).isEqualTo("Event service unavailable");
        }
    }

    @Nested
    @DisplayName("Validation Exceptions")
    class ValidationExceptions {

        @Test
        @DisplayName("should return 413 for MaxUploadSizeExceededException")
        void shouldReturn413ForMaxUploadSizeExceeded() {
            var ex = new MaxUploadSizeExceededException(10485760);

            ResponseEntity<ApiErrorDto> response = handler.handleMaxUploadSize(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
            assertThat(response.getBody().error()).isEqualTo("FILE_TOO_LARGE");
            assertThat(response.getBody().message()).contains("exceeds maximum");
        }

        @Test
        @DisplayName("should return 400 with validation errors for MethodArgumentNotValidException")
        void shouldReturn400WithValidationErrors() throws NoSuchMethodException {
            BindingResult bindingResult = mock(BindingResult.class);
            given(bindingResult.getFieldErrors()).willReturn(List.of(
                    new FieldError("request", "fileName", null, false, null, null, "must not be blank"),
                    new FieldError("request", "size", -1, false, null, null, "must be positive")
            ));
            var method = GlobalExceptionHandlerTest.class.getMethod("dummyMethodForTest", String.class);
            var methodParameter = new org.springframework.core.MethodParameter(method, 0);
            var ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

            ResponseEntity<ApiErrorDto> response = handler.handleValidation(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().error()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getBody().validationErrors()).hasSize(2);
            assertThat(response.getBody().validationErrors().get(0).field()).isEqualTo("fileName");
            assertThat(response.getBody().validationErrors().get(1).field()).isEqualTo("size");
        }
    }

    @Nested
    @DisplayName("AWS Exceptions")
    class AwsExceptions {

        @Test
        @DisplayName("should return 503 for S3Exception")
        void shouldReturn503ForS3Exception() {
            var ex = (S3Exception) S3Exception.builder()
                    .message("Access Denied")
                    .awsErrorDetails(AwsErrorDetails.builder()
                            .errorCode("AccessDenied")
                            .errorMessage("Access Denied")
                            .build())
                    .build();

            ResponseEntity<ApiErrorDto> response = handler.handleS3Exception(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().error()).isEqualTo("AWS_S3_ERROR");
        }

        @Test
        @DisplayName("should return 503 for SnsException")
        void shouldReturn503ForSnsException() {
            var ex = (SnsException) SnsException.builder()
                    .message("Topic not found")
                    .awsErrorDetails(AwsErrorDetails.builder()
                            .errorCode("NotFound")
                            .errorMessage("Topic not found")
                            .build())
                    .build();

            ResponseEntity<ApiErrorDto> response = handler.handleSnsException(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().error()).isEqualTo("AWS_SNS_ERROR");
        }
    }

    @Nested
    @DisplayName("Resilience4j Exceptions")
    class Resilience4jExceptions {

        @Test
        @DisplayName("should return 429 for RequestNotPermitted")
        void shouldReturn429ForRequestNotPermitted() {
            var rateLimiter = mock(io.github.resilience4j.ratelimiter.RateLimiter.class);
            var rateLimiterConfig = io.github.resilience4j.ratelimiter.RateLimiterConfig.custom().build();
            given(rateLimiter.getRateLimiterConfig()).willReturn(rateLimiterConfig);
            given(rateLimiter.getName()).willReturn("testRateLimiter");
            var ex = RequestNotPermitted.createRequestNotPermitted(rateLimiter);

            ResponseEntity<ApiErrorDto> response = handler.handleRateLimitExceeded(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getBody().error()).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(response.getHeaders().get("Retry-After")).containsExactly("1");
        }

        @Test
        @DisplayName("should return 503 for BulkheadFullException")
        void shouldReturn503ForBulkheadFullException() {
            var bulkhead = mock(io.github.resilience4j.bulkhead.Bulkhead.class);
            var bulkheadConfig = io.github.resilience4j.bulkhead.BulkheadConfig.custom().build();
            given(bulkhead.getBulkheadConfig()).willReturn(bulkheadConfig);
            given(bulkhead.getName()).willReturn("testBulkhead");
            var ex = BulkheadFullException.createBulkheadFullException(bulkhead);

            ResponseEntity<ApiErrorDto> response = handler.handleBulkheadFull(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().error()).isEqualTo("SERVICE_OVERLOADED");
            assertThat(response.getHeaders().get("Retry-After")).containsExactly("5");
        }

        @Test
        @DisplayName("should return 503 for CallNotPermittedException")
        void shouldReturn503ForCallNotPermittedException() {
            var cb = mock(io.github.resilience4j.circuitbreaker.CircuitBreaker.class);
            var cbConfig = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom().build();
            given(cb.getName()).willReturn("testCB");
            given(cb.getCircuitBreakerConfig()).willReturn(cbConfig);
            var ex = CallNotPermittedException.createCallNotPermittedException(cb);

            ResponseEntity<ApiErrorDto> response = handler.handleCircuitBreakerOpen(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().error()).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(response.getHeaders().get("Retry-After")).containsExactly("30");
        }
    }

    @Nested
    @DisplayName("Idempotency Exceptions")
    class IdempotencyExceptions {

        @Test
        @DisplayName("should return 409 for IdempotencyConflictException")
        void shouldReturn409ForIdempotencyConflictException() {
            var ex = new IdempotencyConflictException("Request already in progress");

            ResponseEntity<ApiErrorDto> response = handler.handleIdempotencyConflict(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().error()).isEqualTo("IDEMPOTENCY_CONFLICT");
            assertThat(response.getBody().message()).isEqualTo("Request already in progress");
        }
    }

    @Nested
    @DisplayName("Generic Exceptions")
    class GenericExceptions {

        @Test
        @DisplayName("should return 500 for generic exceptions")
        void shouldReturn500ForGenericExceptions() {
            var ex = new RuntimeException("Something unexpected happened");

            ResponseEntity<ApiErrorDto> response = handler.handleGeneric(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().error()).isEqualTo("INTERNAL_ERROR");
            assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        }

        @Test
        @DisplayName("should include correlationId in error response")
        void shouldIncludeCorrelationIdInErrorResponse() {
            MDC.put("correlationId", "unique-correlation-id");
            var ex = new RuntimeException("Error");

            ResponseEntity<ApiErrorDto> response = handler.handleGeneric(ex, webRequest);

            assertThat(response.getBody().correlationId()).isEqualTo("unique-correlation-id");
        }
    }

    @Nested
    @DisplayName("Custom Exceptions")
    class CustomExceptions {

        @Test
        @DisplayName("should return 429 for RateLimitExceededException")
        void shouldReturn429ForRateLimitExceededException() {
            var ex = new RateLimitExceededException("Custom rate limit message");

            ResponseEntity<ApiErrorDto> response = handler.handleCustomRateLimitExceeded(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getBody().error()).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(response.getBody().message()).isEqualTo("Custom rate limit message");
        }

        @Test
        @DisplayName("should return 503 for ServiceUnavailableException")
        void shouldReturn503ForServiceUnavailableException() {
            var ex = new ServiceUnavailableException("Service is down");

            ResponseEntity<ApiErrorDto> response = handler.handleServiceUnavailable(ex, webRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().error()).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(response.getBody().message()).isEqualTo("Service is down");
        }
    }

    @Nested
    @DisplayName("Error Response Format")
    class ErrorResponseFormat {

        @Test
        @DisplayName("should build error with proper type URL")
        void shouldBuildErrorWithProperTypeUrl() {
            var ex = new FileValidationException("Test error");

            ResponseEntity<ApiErrorDto> response = handler.handleFileValidation(ex, webRequest);

            assertThat(response.getBody().type())
                    .isEqualTo("https://api.fsamp.io/errors/file-validation-error");
        }

        @Test
        @DisplayName("should include status code in response body")
        void shouldIncludeStatusCodeInResponseBody() {
            var ex = new FileNotFoundException("Not found");

            ResponseEntity<ApiErrorDto> response = handler.handleFileNotFound(ex, webRequest);

            assertThat(response.getBody().status()).isEqualTo(404);
        }
    }

    public void dummyMethodForTest(String param) {
    }
}
