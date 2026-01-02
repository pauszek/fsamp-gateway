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
            // given
            var ex = new FileValidationException("Invalid file content");

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleFileValidation(ex, webRequest);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().error()).isEqualTo("FILE_VALIDATION_ERROR");
            assertThat(response.getBody().message()).isEqualTo("Invalid file content");
            assertThat(response.getBody().path()).isEqualTo("/api/v1/files");
            assertThat(response.getBody().correlationId()).isEqualTo("test-corr-123");
        }

        @Test
        @DisplayName("should return 404 for FileNotFoundException")
        void shouldReturn404ForFileNotFoundException() {
            // given
            var ex = new FileNotFoundException("File abc-123 not found");

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleFileNotFound(ex, webRequest);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().error()).isEqualTo("FILE_NOT_FOUND");
            assertThat(response.getBody().message()).isEqualTo("File abc-123 not found");
        }

        @Test
        @DisplayName("should return 503 for StorageException")
        void shouldReturn503ForStorageException() {
            // given
            var ex = new StorageException("S3 connection failed");

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleStorage(ex, webRequest);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().error()).isEqualTo("STORAGE_ERROR");
            assertThat(response.getBody().message()).isEqualTo("Storage service unavailable");
        }

        @Test
        @DisplayName("should return 503 for EventPublishException")
        void shouldReturn503ForEventPublishException() {
            // given
            var ex = new EventPublishException("SNS publish failed");

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleEventPublish(ex, webRequest);

            // then
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
            // given
            var ex = new MaxUploadSizeExceededException(10485760);

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleMaxUploadSize(ex, webRequest);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
            assertThat(response.getBody().error()).isEqualTo("FILE_TOO_LARGE");
            assertThat(response.getBody().message()).contains("exceeds maximum");
        }

        @Test
        @DisplayName("should return 400 with validation errors for MethodArgumentNotValidException")
        void shouldReturn400WithValidationErrors() throws NoSuchMethodException {
            // given
            BindingResult bindingResult = mock(BindingResult.class);
            given(bindingResult.getFieldErrors()).willReturn(List.of(
                    new FieldError("request", "fileName", null, false, null, null, "must not be blank"),
                    new FieldError("request", "size", -1, false, null, null, "must be positive")
            ));
            // Create a proper MethodParameter for the exception
            var method = GlobalExceptionHandlerTest.class.getMethod("dummyMethodForTest", String.class);
            var methodParameter = new org.springframework.core.MethodParameter(method, 0);
            var ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleValidation(ex, webRequest);

            // then
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
            // given
            var ex = (S3Exception) S3Exception.builder()
                    .message("Access Denied")
                    .awsErrorDetails(AwsErrorDetails.builder()
                            .errorCode("AccessDenied")
                            .errorMessage("Access Denied")
                            .build())
                    .build();

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleS3Exception(ex, webRequest);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().error()).isEqualTo("AWS_S3_ERROR");
        }

        @Test
        @DisplayName("should return 503 for SnsException")
        void shouldReturn503ForSnsException() {
            // given
            var ex = (SnsException) SnsException.builder()
                    .message("Topic not found")
                    .awsErrorDetails(AwsErrorDetails.builder()
                            .errorCode("NotFound")
                            .errorMessage("Topic not found")
                            .build())
                    .build();

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleSnsException(ex, webRequest);

            // then
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
            // given
            var rateLimiter = mock(io.github.resilience4j.ratelimiter.RateLimiter.class);
            var rateLimiterConfig = io.github.resilience4j.ratelimiter.RateLimiterConfig.custom().build();
            given(rateLimiter.getRateLimiterConfig()).willReturn(rateLimiterConfig);
            given(rateLimiter.getName()).willReturn("testRateLimiter");
            var ex = RequestNotPermitted.createRequestNotPermitted(rateLimiter);

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleRateLimitExceeded(ex, webRequest);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getBody().error()).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(response.getHeaders().get("Retry-After")).containsExactly("1");
        }

        @Test
        @DisplayName("should return 503 for BulkheadFullException")
        void shouldReturn503ForBulkheadFullException() {
            // given
            var bulkhead = mock(io.github.resilience4j.bulkhead.Bulkhead.class);
            var bulkheadConfig = io.github.resilience4j.bulkhead.BulkheadConfig.custom().build();
            given(bulkhead.getBulkheadConfig()).willReturn(bulkheadConfig);
            given(bulkhead.getName()).willReturn("testBulkhead");
            var ex = BulkheadFullException.createBulkheadFullException(bulkhead);

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleBulkheadFull(ex, webRequest);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().error()).isEqualTo("SERVICE_OVERLOADED");
            assertThat(response.getHeaders().get("Retry-After")).containsExactly("5");
        }

        @Test
        @DisplayName("should return 503 for CallNotPermittedException")
        void shouldReturn503ForCallNotPermittedException() {
            // given
            var cb = mock(io.github.resilience4j.circuitbreaker.CircuitBreaker.class);
            var cbConfig = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom().build();
            given(cb.getName()).willReturn("testCB");
            given(cb.getCircuitBreakerConfig()).willReturn(cbConfig);
            var ex = CallNotPermittedException.createCallNotPermittedException(cb);

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleCircuitBreakerOpen(ex, webRequest);

            // then
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
            // given
            var ex = new IdempotencyConflictException("Request already in progress");

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleIdempotencyConflict(ex, webRequest);

            // then
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
            // given
            var ex = new RuntimeException("Something unexpected happened");

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleGeneric(ex, webRequest);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().error()).isEqualTo("INTERNAL_ERROR");
            assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        }

        @Test
        @DisplayName("should include correlationId in error response")
        void shouldIncludeCorrelationIdInErrorResponse() {
            // given
            MDC.put("correlationId", "unique-correlation-id");
            var ex = new RuntimeException("Error");

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleGeneric(ex, webRequest);

            // then
            assertThat(response.getBody().correlationId()).isEqualTo("unique-correlation-id");
        }
    }

    @Nested
    @DisplayName("Custom Exceptions")
    class CustomExceptions {

        @Test
        @DisplayName("should return 429 for RateLimitExceededException")
        void shouldReturn429ForRateLimitExceededException() {
            // given
            var ex = new RateLimitExceededException("Custom rate limit message");

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleCustomRateLimitExceeded(ex, webRequest);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getBody().error()).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(response.getBody().message()).isEqualTo("Custom rate limit message");
        }

        @Test
        @DisplayName("should return 503 for ServiceUnavailableException")
        void shouldReturn503ForServiceUnavailableException() {
            // given
            var ex = new ServiceUnavailableException("Service is down");

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleServiceUnavailable(ex, webRequest);

            // then
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
            // given
            var ex = new FileValidationException("Test error");

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleFileValidation(ex, webRequest);

            // then
            assertThat(response.getBody().type())
                    .isEqualTo("https://api.fsamp.io/errors/file-validation-error");
        }

        @Test
        @DisplayName("should include status code in response body")
        void shouldIncludeStatusCodeInResponseBody() {
            // given
            var ex = new FileNotFoundException("Not found");

            // when
            ResponseEntity<ApiErrorDto> response = handler.handleFileNotFound(ex, webRequest);

            // then
            assertThat(response.getBody().status()).isEqualTo(404);
        }
    }

    // Helper method for MethodParameter creation in test
    public void dummyMethodForTest(String param) {
        // Dummy method used only for creating MethodParameter in tests
    }
}
