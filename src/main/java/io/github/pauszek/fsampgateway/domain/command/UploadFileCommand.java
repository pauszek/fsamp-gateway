package io.github.pauszek.fsampgateway.domain.command;

import io.github.pauszek.fsampgateway.domain.model.CorrelationId;
import io.github.pauszek.fsampgateway.domain.model.FileId;
import lombok.Builder;
import lombok.Getter;

import java.io.InputStream;
import java.util.Objects;
import java.util.Set;

@Getter
@Builder
public final class UploadFileCommand {

    private final String fileName;
    private final String contentType;
    private final long size;
    private final InputStream content;
    private final String correlationId;
    private final String uploadedBy;
    private final String description;
    private final Set<String> tags;
    private final FileId fileId;

    @SuppressWarnings("java:S107")
    public UploadFileCommand(
            String fileName,
            String contentType,
            long size,
            InputStream content,
            String correlationId,
            String uploadedBy,
            String description,
            Set<String> tags,
            FileId fileId
    ) {
        this.fileName = Objects.requireNonNull(fileName, "File name is required");
        this.contentType = contentType;
        this.size = size;
        this.content = Objects.requireNonNull(content, "Content is required");
        this.correlationId = correlationId;
        this.uploadedBy = uploadedBy;
        this.description = description;
        this.tags = tags == null ? Set.of() : Set.copyOf(tags);
        this.fileId = fileId;
    }

    public CorrelationId getCorrelationIdOrGenerate() {
        return CorrelationId.of(correlationId);
    }

    public FileId getFileIdOrGenerate() {
        return fileId == null ? FileId.generate() : fileId;
    }
}
