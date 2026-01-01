package io.github.pauszek.fsampgateway.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for file upload.
 */
@Schema(description = "File upload request metadata")
public record FileUploadRequestDto(
        
        @Schema(description = "Optional correlation ID for distributed tracing")
        String correlationId,
        
        @Schema(description = "Optional description of the file")
        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,
        
        @Schema(description = "Optional tags for categorization")
        @Size(max = 10, message = "Maximum 10 tags allowed")
        String[] tags
) {
    public FileUploadRequestDto {
        if (tags == null) {
            tags = new String[0];
        }
    }
}
