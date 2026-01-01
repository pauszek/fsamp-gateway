package io.github.pauszek.fsampgateway.application.mapper;

import io.github.pauszek.fsampgateway.application.dto.FileUploadResponseDto;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for SecureFile entity to DTOs.
 */
@Mapper(componentModel = "spring")
public interface FileMapper {

    @Mapping(target = "fileId", expression = "java(file.getId().value())")
    @Mapping(target = "correlationId", expression = "java(file.getCorrelationId().value())")
    @Mapping(target = "filename", expression = "java(file.getFileName().value())")
    @Mapping(target = "sizeBytes", expression = "java(file.getSize().bytes())")
    @Mapping(target = "sizeHuman", expression = "java(file.getSize().toHumanReadable())")
    @Mapping(target = "mimeType", expression = "java(file.getMimeType().value())")
    @Mapping(target = "checksum", expression = "java(file.getChecksum() != null ? file.getChecksum().value() : null)")
    @Mapping(target = "status", expression = "java(file.getStatus().name())")
    @Mapping(target = "statusDescription", expression = "java(file.getStatus().getDescription())")
    @Mapping(target = "uploadedAt", expression = "java(file.getAuditInfo().createdAt())")
    @Mapping(target = "message", constant = "File uploaded successfully and queued for processing")
    FileUploadResponseDto toResponseDto(SecureFile file);
}
