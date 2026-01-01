package io.github.pauszek.fsampgateway.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Standard API error response following RFC 7807 Problem Details.
 */
@Schema(description = "API error response")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorDto(
        
        @Schema(description = "Error type URI", example = "https://api.fsamp.io/errors/validation-error")
        String type,
        
        @Schema(description = "HTTP status code", example = "400")
        int status,
        
        @Schema(description = "Error code for client handling", example = "VALIDATION_ERROR")
        String error,
        
        @Schema(description = "Human-readable error message")
        String message,
        
        @Schema(description = "Detailed error description")
        String detail,
        
        @Schema(description = "Request path that caused the error", example = "/api/v1/files/upload")
        String path,
        
        @Schema(description = "Correlation ID for troubleshooting")
        String correlationId,
        
        @Schema(description = "Error timestamp")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Instant timestamp,
        
        @Schema(description = "Validation errors if applicable")
        List<ValidationErrorDto> validationErrors
) {
    public static Builder builder() {
        return new Builder();
    }

    @Schema(description = "Validation error detail")
    public record ValidationErrorDto(
            @Schema(description = "Field name", example = "file")
            String field,
            
            @Schema(description = "Error message", example = "File is required")
            String message,
            
            @Schema(description = "Rejected value")
            Object rejectedValue
    ) {}

    public static class Builder {
        private String type;
        private int status;
        private String error;
        private String message;
        private String detail;
        private String path;
        private String correlationId;
        private Instant timestamp = Instant.now();
        private List<ValidationErrorDto> validationErrors;

        public Builder type(String type) { this.type = type; return this; }
        public Builder status(int status) { this.status = status; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder detail(String detail) { this.detail = detail; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder validationErrors(List<ValidationErrorDto> validationErrors) { 
            this.validationErrors = validationErrors; 
            return this; 
        }

        public ApiErrorDto build() {
            return new ApiErrorDto(
                    type, status, error, message, detail, 
                    path, correlationId, timestamp, validationErrors
            );
        }
    }
}
