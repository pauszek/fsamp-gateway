package io.github.pauszek.fsampgateway.adapter.in.web;

import io.github.pauszek.fsampgateway.application.dto.ApiErrorDto;
import io.github.pauszek.fsampgateway.application.dto.FileUploadRequestDto;
import io.github.pauszek.fsampgateway.application.dto.FileUploadResponseDto;
import io.github.pauszek.fsampgateway.application.mapper.FileMapper;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;
import io.github.pauszek.fsampgateway.domain.port.in.UploadFileUseCase;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST Adapter - File Upload Controller.
 * 
 * Primary adapter that exposes the file upload use case via REST API.
 * 
 * API versioning: URL path versioning (v1)
 */
@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "Files", description = "File upload and management operations")
@Validated
public class FileUploadRestAdapter {

    private static final Logger log = LoggerFactory.getLogger(FileUploadRestAdapter.class);

    private final UploadFileUseCase uploadFileUseCase;
    private final FileMapper fileMapper;

    public FileUploadRestAdapter(UploadFileUseCase uploadFileUseCase, FileMapper fileMapper) {
        this.uploadFileUseCase = uploadFileUseCase;
        this.fileMapper = fileMapper;
    }

    @Operation(
            summary = "Upload a file",
            description = """
                    Upload a file to the FSAMP platform for secure storage and processing.
                    
                    The file will be:
                    1. Validated (size, content type)
                    2. Encrypted and stored in S3 using KMS (FIPS 140-3 compliant)
                    3. An event will be published for async processing
                    
                    **Allowed file types:** PDF, PNG, JPEG, JSON, XML, TXT, CSV
                    **Max file size:** 100MB
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "File uploaded successfully",
                    content = @Content(schema = @Schema(implementation = FileUploadResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request (validation error)",
                    content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
            ),
            @ApiResponse(
                    responseCode = "413",
                    description = "File too large",
                    content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
            ),
            @ApiResponse(
                    responseCode = "415",
                    description = "Unsupported media type",
                    content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Service unavailable (storage or messaging error)",
                    content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
            )
    })
    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Timed(value = "file.upload", description = "Time taken to upload a file")
    public ResponseEntity<FileUploadResponseDto> uploadFile(
            @Parameter(description = "File to upload", required = true)
            @RequestParam("file") MultipartFile file,
            
            @Parameter(description = "Optional upload metadata")
            @ModelAttribute FileUploadRequestDto request
    ) throws IOException {
        log.info("Received upload request: filename={}, size={}, contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        UploadFileUseCase.UploadFileCommand command = new UploadFileUseCase.UploadFileCommand(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream(),
                request != null ? request.correlationId() : null,
                "ANONYMOUS" // TODO: Get from security context
        );

        SecureFile uploadedFile = uploadFileUseCase.execute(command);
        FileUploadResponseDto response = fileMapper.toResponseDto(uploadedFile);

        log.info("Upload successful: fileId={}, status={}", 
                uploadedFile.getId(), uploadedFile.getStatus());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get file metadata",
            description = "Retrieve metadata for a previously uploaded file"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "File metadata retrieved",
                    content = @Content(schema = @Schema(implementation = FileUploadResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "File not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
            )
    })
    @GetMapping("/{fileId}")
    @Timed(value = "file.get", description = "Time taken to get file metadata")
    public ResponseEntity<FileUploadResponseDto> getFile(
            @Parameter(description = "File ID", required = true)
            @PathVariable String fileId
    ) {
        log.info("Get file request: fileId={}", fileId);
        // TODO: Implement GetFileUseCase
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
