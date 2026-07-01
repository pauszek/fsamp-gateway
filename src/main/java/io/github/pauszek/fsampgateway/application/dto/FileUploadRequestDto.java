package io.github.pauszek.fsampgateway.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.Arrays;
import java.util.Objects;

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
        tags = tags == null ? new String[0] : tags.clone();
    }

    @Override
    public String[] tags() {
        return tags.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FileUploadRequestDto(
                String otherCorrelationId,
                String otherDescription,
                String[] otherTags
        ))) {
            return false;
        }
        return Objects.equals(correlationId, otherCorrelationId)
                && Objects.equals(description, otherDescription)
                && Arrays.equals(tags, otherTags);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(correlationId, description);
        result = 31 * result + Arrays.hashCode(tags);
        return result;
    }

    @Override
    public String toString() {
        return "FileUploadRequestDto[correlationId=" + correlationId
                + ", description=" + description
                + ", tags=" + Arrays.toString(tags) + "]";
    }
}
