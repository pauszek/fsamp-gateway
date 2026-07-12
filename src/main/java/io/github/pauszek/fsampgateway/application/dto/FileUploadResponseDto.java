package io.github.pauszek.fsampgateway.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;
import java.util.Set;

@Schema(description = "File upload response")
public record FileUploadResponseDto(

        @Schema(description = "Unique file identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID fileId,

        @Schema(description = "Correlation ID for tracking", example = "abc123def456")
        String correlationId,

        @Schema(description = "Original filename", example = "document.pdf")
        String filename,

        @Schema(description = "Optional upload description")
        String description,

        @Schema(description = "Upload tags")
        Set<String> tags,

        @Schema(description = "File size in bytes", example = "1024000")
        long sizeBytes,

        @Schema(description = "Human-readable file size", example = "1000.00 KB")
        String sizeHuman,

        @Schema(description = "Detected MIME type", example = "application/pdf")
        String mimeType,

        @Schema(description = "File checksum (SHA-256)")
        String checksum,

        @Schema(description = "Processing status", example = "UPLOADED")
        String status,

        @Schema(description = "Status description", example = "File stored, awaiting processing")
        String statusDescription,

        @Schema(description = "Upload timestamp")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Instant uploadedAt,

        @Schema(description = "Success message")
        String message
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID fileId;
        private String correlationId;
        private String filename;
        private String description;
        private Set<String> tags = Set.of();
        private long sizeBytes;
        private String sizeHuman;
        private String mimeType;
        private String checksum;
        private String status;
        private String statusDescription;
        private Instant uploadedAt;
        private String message;

        public Builder fileId(UUID fileId) { this.fileId = fileId; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder filename(String filename) { this.filename = filename; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder tags(Set<String> tags) { this.tags = tags == null ? Set.of() : Set.copyOf(tags); return this; }
        public Builder sizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; return this; }
        public Builder sizeHuman(String sizeHuman) { this.sizeHuman = sizeHuman; return this; }
        public Builder mimeType(String mimeType) { this.mimeType = mimeType; return this; }
        public Builder checksum(String checksum) { this.checksum = checksum; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder statusDescription(String statusDescription) { this.statusDescription = statusDescription; return this; }
        public Builder uploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; return this; }
        public Builder message(String message) { this.message = message; return this; }

        public FileUploadResponseDto build() {
            return new FileUploadResponseDto(
                    fileId, correlationId, filename, description, tags, sizeBytes, sizeHuman,
                    mimeType, checksum, status, statusDescription, uploadedAt, message
            );
        }
    }
}
