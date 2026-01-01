package io.github.pauszek.fsampgateway.domain.command;

import io.github.pauszek.fsampgateway.domain.model.CorrelationId;
import lombok.Builder;
import lombok.Getter;

import java.io.InputStream;
import java.util.Objects;

/**
 * Command object for file upload use case.
 * 
 * Immutable command following CQRS pattern.
 * Contains all data needed to execute the upload workflow.
 */
@Getter
@Builder
public final class UploadFileCommand {

    private final String fileName;
    private final String contentType;
    private final long size;
    private final InputStream content;
    private final String correlationId;
    private final String uploadedBy;

    public UploadFileCommand(
            String fileName,
            String contentType,
            long size,
            InputStream content,
            String correlationId,
            String uploadedBy
    ) {
        this.fileName = Objects.requireNonNull(fileName, "File name is required");
        this.contentType = contentType;
        this.size = size;
        this.content = Objects.requireNonNull(content, "Content is required");
        this.correlationId = correlationId;
        this.uploadedBy = uploadedBy;
    }

    /**
     * Get correlation ID or generate a new one if not provided.
     */
    public CorrelationId getCorrelationIdOrGenerate() {
        return CorrelationId.of(correlationId);
    }
}
