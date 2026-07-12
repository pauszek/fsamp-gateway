package io.github.pauszek.fsampgateway.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

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
        String @NotBlank(message = "Tags cannot be blank")
        @Size(max = 50, message = "A tag must not exceed 50 characters") [] tags
) {
    public FileUploadRequestDto {
        tags = tags == null ? new String[0] : tags.clone();
    }

    @Override
    public String[] tags() {
        return tags.clone();
    }

    @Override
    @SuppressWarnings("java:S6878")
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FileUploadRequestDto otherRequest)) {
            return false;
        }
        return Objects.equals(correlationId, otherRequest.correlationId())
                && Objects.equals(description, otherRequest.description())
                && Arrays.equals(tags, otherRequest.tags());
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
