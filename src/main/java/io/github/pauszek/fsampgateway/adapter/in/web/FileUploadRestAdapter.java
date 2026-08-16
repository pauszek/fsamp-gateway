package io.github.pauszek.fsampgateway.adapter.in.web;

import io.github.pauszek.fsampgateway.application.dto.ApiErrorDto;
import io.github.pauszek.fsampgateway.application.dto.FileUploadRequestDto;
import io.github.pauszek.fsampgateway.application.dto.FileUploadResponseDto;
import io.github.pauszek.fsampgateway.application.mapper.FileMapper;
import io.github.pauszek.fsampgateway.domain.command.UploadFileCommand;
import io.github.pauszek.fsampgateway.domain.model.CorrelationId;
import io.github.pauszek.fsampgateway.domain.model.FileId;
import io.github.pauszek.fsampgateway.domain.model.FileName;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;
import io.github.pauszek.fsampgateway.domain.model.UserPrincipal;
import io.github.pauszek.fsampgateway.domain.port.in.DeleteFileUseCase;
import io.github.pauszek.fsampgateway.domain.port.in.GetFileUseCase;
import io.github.pauszek.fsampgateway.domain.port.in.UploadFileUseCase;
import io.github.pauszek.fsampgateway.infrastructure.idempotency.Idempotent;
import io.github.pauszek.fsampgateway.infrastructure.idempotency.IdempotencyAspect;
import io.github.pauszek.fsampgateway.infrastructure.security.cognito.CurrentUserService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.pauszek.fsampgateway.infrastructure.security.CorrelationIdFilter;
import io.github.pauszek.fsampgateway.domain.exception.FileValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "Files", description = "File upload and management operations")
@SecurityRequirement(name = "bearerAuth")
@Validated
@Slf4j
@RequiredArgsConstructor
public class FileUploadRestAdapter {

    private final UploadFileUseCase uploadFileUseCase;
    private final GetFileUseCase getFileUseCase;
    private final DeleteFileUseCase deleteFileUseCase;
    private final FileMapper fileMapper;
    private final CurrentUserService currentUserService;

    @Operation(
            summary = "Upload a file",
            description = """
                    Upload a file to the FSAMP platform for secure storage and processing.

                    The file will be:
                    1. Validated (size, content type)
                    2. Encrypted and stored in S3 using KMS (FIPS 140-3-oriented posture)
                    3. An event will be published for async processing

                    **Allowed file types:** PDF, PNG, JPEG, JSON, XML, TXT, CSV
                    **Max file size:** 9MiB through the AWS API Gateway deployment
                    (the standalone service limit is configurable up to the 100MiB event-contract maximum)

                    **Required permissions:** `files.write` scope OR `users`/`admins` group membership

                    **Idempotency:** Send `X-Idempotency-Key` header for safe retries
                    """
    )
    @ApiResponse(
            responseCode = "201",
            description = "File uploaded successfully",
            content = @Content(schema = @Schema(implementation = FileUploadResponseDto.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request (validation error)",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = "Idempotency key conflict - request already in progress",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @ApiResponse(
            responseCode = "413",
            description = "File too large",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @ApiResponse(
            responseCode = "415",
            description = "Unsupported media type",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @ApiResponse(
            responseCode = "429",
            description = "Rate limit exceeded",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @ApiResponse(
            responseCode = "503",
            description = "Service unavailable (storage or messaging error)",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasAuthority('SCOPE_files.write') or "
            + "(@authorizationPolicy.isGroupFallbackAllowed() and hasAnyRole('USERS', 'ADMINS'))")
    @Timed(value = "file.upload", description = "Time taken to upload a file")
    @RateLimiter(name = "fileUpload")
    @Bulkhead(name = "fileUpload")
    @Idempotent(responseType = FileUploadResponseDto.class)
    public ResponseEntity<FileUploadResponseDto> uploadFile(
            @Parameter(description = "File to upload", required = true)
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "Optional upload metadata")
            @Valid @ModelAttribute FileUploadRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) throws IOException {
        UserPrincipal currentUser = currentUserService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User not found in security context"));

        log.info("Received upload request: filename={}, size={}, contentType={}, userId={}",
                FileName.safeForLogs(file.getOriginalFilename()),
                file.getSize(),
                file.getContentType(),
                currentUser.userId());

        CorrelationId correlationId = resolveCorrelationId(request, httpRequest);
        MDC.put(CorrelationIdFilter.MDC_CORRELATION_ID, correlationId.value());
        httpResponse.setHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId.value());

        UploadFileCommand command = UploadFileCommand.builder()
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .content(file.getInputStream())
                .correlationId(correlationId.value())
                .uploadedBy(currentUser.userId())
                .description(request != null ? request.description() : null)
                .tags(request == null
                        ? java.util.Set.of()
                        : request.tags().stream()
                                .map(String::trim)
                                .collect(java.util.stream.Collectors.toUnmodifiableSet()))
                .fileId(resolveReservedFileId(httpRequest))
                .build();

        SecureFile uploadedFile = uploadFileUseCase.execute(command);
        FileUploadResponseDto response = fileMapper.toResponseDto(uploadedFile);

        log.info("Upload successful: fileId={}, status={}, userId={}",
                uploadedFile.getId(), uploadedFile.getStatus(), currentUser.userId());

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/files/{fileId}")
                .buildAndExpand(uploadedFile.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Operation(
            summary = "Get file metadata",
            description = """
                    Retrieve metadata for a previously uploaded file.

                    **Required permissions:** `files.read` scope OR `users`/`admins` group membership
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "File metadata retrieved",
            content = @Content(schema = @Schema(implementation = FileUploadResponseDto.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "File not found",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @GetMapping("/{fileId}")
    @PreAuthorize("hasAuthority('SCOPE_files.read') or "
            + "(@authorizationPolicy.isGroupFallbackAllowed() and hasAnyRole('USERS', 'ADMINS'))")
    @Timed(value = "file.get", description = "Time taken to get file metadata")
    @RateLimiter(name = "fileDownload")
    public ResponseEntity<FileUploadResponseDto> getFile(
            @Parameter(description = "File ID", required = true)
            @PathVariable String fileId
    ) {
        UserPrincipal currentUser = currentUserService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User not found in security context"));
        log.info("Get file request: fileId={}, userId={}", fileId, currentUser.userId());

        SecureFile file = getFileUseCase.getByIdOrThrow(FileId.of(fileId));
        verifyFileAccess(file, currentUser);
        FileUploadResponseDto response = fileMapper.toResponseDto(file);

        return ResponseEntity.ok(response);
    }

    private void verifyFileAccess(SecureFile file, UserPrincipal currentUser) {
        if (currentUser.isAdmin()) {
            return;
        }

        String owner = file.getAuditInfo().createdBy();
        if (!currentUser.userId().equals(owner)) {
            log.warn("Denied file metadata access: fileId={}, owner={}, requester={}",
                    file.getId(), owner, currentUser.userId());
            throw new AccessDeniedException("File metadata is not accessible for the current user");
        }
    }

    @Operation(
            summary = "Delete a file",
            description = """
                    Delete a file from the platform.

                    **Required permissions:** `admins` group membership only
                    """
    )
    @ApiResponse(
            responseCode = "204",
            description = "File deleted successfully"
    )
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @ApiResponse(
            responseCode = "403",
            description = "Admin role required",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "File not found",
            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
    )
    @DeleteMapping("/{fileId}")
    @PreAuthorize("hasRole('ADMINS')")
    @Timed(value = "file.delete", description = "Time taken to delete a file")
    public ResponseEntity<Void> deleteFile(
            @Parameter(description = "File ID", required = true)
            @PathVariable String fileId
    ) {
        String userId = currentUserService.getCurrentUserId().orElse("unknown");
        log.info("Delete file request: fileId={}, userId={}", fileId, userId);

        deleteFileUseCase.execute(FileId.of(fileId));

        log.info("File deleted successfully: fileId={}, userId={}", fileId, userId);
        return ResponseEntity.noContent().build();
    }

    private static CorrelationId resolveCorrelationId(
            FileUploadRequestDto metadata,
            HttpServletRequest request
    ) {
        String filtered = (String) request.getAttribute(CorrelationIdFilter.REQUEST_CORRELATION_ID);
        CorrelationId headerCorrelation = CorrelationId.of(filtered);
        if (metadata == null || metadata.correlationId() == null || metadata.correlationId().isBlank()) {
            return headerCorrelation;
        }
        CorrelationId formCorrelation;
        try {
            formCorrelation = CorrelationId.of(metadata.correlationId());
        } catch (IllegalArgumentException e) {
            throw new FileValidationException(e.getMessage(), e);
        }
        String suppliedHeader = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        if (suppliedHeader != null && !suppliedHeader.isBlank() && !formCorrelation.equals(headerCorrelation)) {
            throw new FileValidationException(
                    "Correlation ID in multipart metadata must match X-Correlation-ID");
        }
        return formCorrelation;
    }

    private static FileId resolveReservedFileId(HttpServletRequest request) {
        Object operationId = request.getAttribute(IdempotencyAspect.OPERATION_ID_ATTRIBUTE);
        if (operationId == null) {
            return null;
        }
        if (operationId instanceof String value) {
            return FileId.of(value);
        }
        throw new IllegalStateException("Invalid internal idempotency operation identifier");
    }

}
