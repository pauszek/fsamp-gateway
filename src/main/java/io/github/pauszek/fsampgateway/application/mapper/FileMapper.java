package io.github.pauszek.fsampgateway.application.mapper;

import io.github.pauszek.fsampgateway.application.dto.FileUploadResponseDto;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;
import org.springframework.stereotype.Component;

@Component
public class FileMapper {

    public FileUploadResponseDto toResponseDto(SecureFile file) {
        if (file == null) {
            return null;
        }

        return FileUploadResponseDto.builder()
                .fileId(file.getId().value())
                .correlationId(file.getCorrelationId().value())
                .filename(file.getFileName().value())
                .description(file.getDescription())
                .tags(file.getTags())
                .sizeBytes(file.getSize().bytes())
                .sizeHuman(file.getSize().toHumanReadable())
                .mimeType(file.getMimeType().value())
                .checksum(file.getChecksum() != null ? file.getChecksum().value() : null)
                .status(file.getStatus().name())
                .statusDescription(file.getStatus().getDescription())
                .uploadedAt(file.getAuditInfo().createdAt())
                .message(messageFor(file))
                .build();
    }

    private static String messageFor(SecureFile file) {
        return switch (file.getStatus()) {
            case PENDING, UPLOADED -> "File uploaded successfully and queued for processing";
            case SCANNING, PROCESSING -> "File processing is in progress";
            case COMPLETED -> "File processing completed";
            case FAILED -> "File processing failed";
            case DELETING -> "File deletion is in progress";
        };
    }
}
