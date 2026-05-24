package io.github.pauszek.fsampgateway.domain.command;

import io.github.pauszek.fsampgateway.domain.model.CorrelationId;
import lombok.Builder;
import lombok.Getter;

import java.io.InputStream;
import java.util.Objects;

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

    public CorrelationId getCorrelationIdOrGenerate() {
        return CorrelationId.of(correlationId);
    }
}
