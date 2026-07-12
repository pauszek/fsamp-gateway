package io.github.pauszek.fsampgateway.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Schema(description = "File upload request metadata")
public record FileUploadRequestDto(

        @Schema(description = "Optional correlation ID for distributed tracing")
        String correlationId,

        @Schema(description = "Optional description of the file")
        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @Schema(description = "Optional tags for categorization")
        @Size(max = 10, message = "Maximum 10 tags allowed")
        // Container element constraints require a List; Hibernate Validator
        // silently ignores type-use annotations on array components.
        List<@NotBlank(message = "Tags cannot be blank")
        @Size(max = 50, message = "A tag must not exceed 50 characters") String> tags
) {
    public FileUploadRequestDto {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
