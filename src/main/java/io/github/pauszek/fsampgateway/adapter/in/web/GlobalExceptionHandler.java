package io.github.pauszek.fsampgateway.adapter.in.web;

import io.github.pauszek.fsampgateway.application.dto.ApiErrorDto;
import io.github.pauszek.fsampgateway.domain.exception.*;
import io.github.pauszek.fsampgateway.infrastructure.idempotency.IdempotencyConflictException;
import io.github.pauszek.fsampgateway.infrastructure.idempotency.InvalidIdempotencyKeyException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.util.List;
import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_TYPE_BASE = "https://api.fsamp.io/errors/";
    private static final String RETRY_AFTER_HEADER = "Retry-After";
    private static final String RETRY_AFTER_SHORT_DELAY = "1";
    private static final String RETRY_AFTER_OVERLOAD_DELAY = "5";
    private static final String RETRY_AFTER_CIRCUIT_BREAKER_DELAY = "30";

    @ExceptionHandler(FileValidationException.class)
    public ResponseEntity<ApiErrorDto> handleFileValidation(
            FileValidationException ex, WebRequest request) {

        log.warn("File validation failed: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(
                buildError(HttpStatus.BAD_REQUEST, ex.getErrorCode(),
                        ex.getMessage(), request)
        );
    }

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<ApiErrorDto> handleFileNotFound(
            FileNotFoundException ex, WebRequest request) {

        log.warn("File not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                buildError(HttpStatus.NOT_FOUND, ex.getErrorCode(),
                        ex.getMessage(), request)
        );
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiErrorDto> handleStorage(
            StorageException ex, WebRequest request) {

        log.error("Storage operation failed: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                buildError(HttpStatus.SERVICE_UNAVAILABLE, ex.getErrorCode(),
                        "Storage service unavailable", request)
        );
    }

    @ExceptionHandler(EventPublishException.class)
    public ResponseEntity<ApiErrorDto> handleEventPublish(
            EventPublishException ex, WebRequest request) {

        log.error("Event publishing failed: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                buildError(HttpStatus.SERVICE_UNAVAILABLE, ex.getErrorCode(),
                        "Event service unavailable", request)
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorDto> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, WebRequest request) {

        log.warn("File size exceeded: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                buildError(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                        "File size exceeds maximum allowed limit", request)
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorDto> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, WebRequest request) {

        log.warn("Unsupported request media type: {}", ex.getContentType());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(
                buildError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                        "Content type is not supported", request)
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorDto> handleMissingRequestPart(
            MissingServletRequestPartException ex, WebRequest request) {

        log.warn("Required multipart request part is missing: {}", ex.getRequestPartName());

        return ResponseEntity.badRequest().body(
                buildError(HttpStatus.BAD_REQUEST, "MISSING_REQUEST_PART",
                        "Required multipart file is missing", request)
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {

        log.warn("Validation error: {}", ex.getMessage());

        List<ApiErrorDto.ValidationErrorDto> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiErrorDto.ValidationErrorDto(
                        error.getField(),
                        error.getDefaultMessage(),
                        error.getRejectedValue()
                ))
                .toList();

        return ResponseEntity.badRequest().body(
                ApiErrorDto.builder()
                        .type(ERROR_TYPE_BASE + "validation-error")
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("VALIDATION_ERROR")
                        .message("Validation failed")
                        .path(getPath(request))
                        .correlationId(MDC.get("correlationId"))
                        .validationErrors(errors)
                        .build()
        );
    }

    @ExceptionHandler(S3Exception.class)
    public ResponseEntity<ApiErrorDto> handleS3Exception(
            S3Exception ex, WebRequest request) {

        log.error("AWS S3 error: {} - {}",
                ex.awsErrorDetails().errorCode(),
                ex.awsErrorDetails().errorMessage(), ex);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                buildError(HttpStatus.SERVICE_UNAVAILABLE, "AWS_S3_ERROR",
                        "Storage service error", request)
        );
    }

    @ExceptionHandler(SnsException.class)
    public ResponseEntity<ApiErrorDto> handleSnsException(
            SnsException ex, WebRequest request) {

        log.error("AWS SNS error: {} - {}",
                ex.awsErrorDetails().errorCode(),
                ex.awsErrorDetails().errorMessage(), ex);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                buildError(HttpStatus.SERVICE_UNAVAILABLE, "AWS_SNS_ERROR",
                        "Messaging service error", request)
        );
    }
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiErrorDto> handleRateLimitExceeded(
            RequestNotPermitted ex, WebRequest request) {

        log.warn("Rate limit exceeded: {}", ex.getMessage());

        HttpHeaders headers = new HttpHeaders();
        headers.add(RETRY_AFTER_HEADER, RETRY_AFTER_SHORT_DELAY);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(buildError(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                        "Too many requests. Please try again later.", request));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorDto> handleCustomRateLimitExceeded(
            RateLimitExceededException ex, WebRequest request) {

        log.warn("Rate limit exceeded: {}", ex.getMessage());

        HttpHeaders headers = new HttpHeaders();
        headers.add(RETRY_AFTER_HEADER, RETRY_AFTER_SHORT_DELAY);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(buildError(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                        ex.getMessage(), request));
    }

    @ExceptionHandler(BulkheadFullException.class)
    public ResponseEntity<ApiErrorDto> handleBulkheadFull(
            BulkheadFullException ex, WebRequest request) {

        log.warn("Bulkhead full (service overloaded): {}", ex.getMessage());

        HttpHeaders headers = new HttpHeaders();
        headers.add(RETRY_AFTER_HEADER, RETRY_AFTER_OVERLOAD_DELAY);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .headers(headers)
                .body(buildError(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_OVERLOADED",
                        "Service is temporarily overloaded. Please try again later.", request));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiErrorDto> handleCircuitBreakerOpen(
            CallNotPermittedException ex, WebRequest request) {

        log.warn("Circuit breaker open: {}", ex.getMessage());

        HttpHeaders headers = new HttpHeaders();
        headers.add(RETRY_AFTER_HEADER, RETRY_AFTER_CIRCUIT_BREAKER_DELAY);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .headers(headers)
                .body(buildError(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                        "Service temporarily unavailable. Please try again later.", request));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiErrorDto> handleServiceUnavailable(
            ServiceUnavailableException ex, WebRequest request) {

        log.warn("Service unavailable: {}", ex.getMessage());

        HttpHeaders headers = new HttpHeaders();
        headers.add(RETRY_AFTER_HEADER, RETRY_AFTER_OVERLOAD_DELAY);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .headers(headers)
                .body(buildError(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                        ex.getMessage(), request));
    }
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiErrorDto> handleIdempotencyConflict(
            IdempotencyConflictException ex, WebRequest request) {

        log.warn("Idempotency conflict: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                buildError(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                        ex.getMessage(), request)
        );
    }

    @ExceptionHandler({InvalidIdempotencyKeyException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorDto> handleInvalidArgument(
            IllegalArgumentException ex, WebRequest request) {
        log.warn("Invalid request argument: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                buildError(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), request)
        );
    }

    // Denials raised inside the controller would otherwise reach handleGeneric
    // and be reported as 500s.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorDto> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {

        log.warn("Access denied: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                buildError(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                        "You don't have permission to access this resource", request)
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleGeneric(
            Exception ex, WebRequest request) {

        log.error("Unexpected error: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "An unexpected error occurred", request)
        );
    }

    private ApiErrorDto buildError(HttpStatus status, String errorCode,
                                   String message, WebRequest request) {
        return ApiErrorDto.builder()
                .type(ERROR_TYPE_BASE + errorCode.toLowerCase(Locale.ROOT).replace('_', '-'))
                .status(status.value())
                .error(errorCode)
                .message(message)
                .path(getPath(request))
                .correlationId(MDC.get("correlationId"))
                .build();
    }

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
