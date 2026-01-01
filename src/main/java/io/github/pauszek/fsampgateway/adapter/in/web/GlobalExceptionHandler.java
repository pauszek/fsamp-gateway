package io.github.pauszek.fsampgateway.adapter.in.web;

import io.github.pauszek.fsampgateway.application.dto.ApiErrorDto;
import io.github.pauszek.fsampgateway.domain.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.util.List;

/**
 * Global Exception Handler.
 * 
 * Converts domain exceptions to standardized API error responses.
 * Follows RFC 7807 Problem Details format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_TYPE_BASE = "https://api.fsamp.io/errors/";

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
                .type(ERROR_TYPE_BASE + errorCode.toLowerCase().replace('_', '-'))
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
