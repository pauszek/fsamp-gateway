package io.github.pauszek.fsampgateway.adapter.in.web;

import io.github.pauszek.fsampgateway.application.dto.ApiErrorDto;
import io.github.pauszek.fsampgateway.application.dto.FileUploadRequestDto;
import io.github.pauszek.fsampgateway.application.dto.FileUploadResponseDto;
import io.github.pauszek.fsampgateway.application.mapper.FileMapper;
import io.github.pauszek.fsampgateway.domain.command.UploadFileCommand;
import io.github.pauszek.fsampgateway.domain.model.FileId;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;
import io.github.pauszek.fsampgateway.domain.model.UserPrincipal;
import io.github.pauszek.fsampgateway.domain.port.in.DeleteFileUseCase;
import io.github.pauszek.fsampgateway.domain.port.in.GetFileUseCase;
import io.github.pauszek.fsampgateway.domain.port.in.UploadFileUseCase;
import io.github.pauszek.fsampgateway.infrastructure.idempotency.Idempotent;
import io.github.pauszek.fsampgateway.infrastructure.security.cognito.CurrentUserService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

/**
 * REST Adapter - File Upload Controller.
 * 
 * Primary adapter that exposes the file upload use case via REST API.
 * 
 * Security:
 * - All endpoints require OAuth2 Bearer token (Cognito JWT)
 * - Upload requires files.write scope or ROLE_USERS/ROLE_ADMINS
 * - Download requires files.read scope or ROLE_USERS/ROLE_ADMINS
 * - Delete requires ROLE_ADMINS only
 * 
 * API versioning: URL path versioning (v1)
 */
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
                    2. Encrypted and stored in S3 using KMS (FIPS 140-3 compliant)
                    3. An event will be published for async processing
                    
                    **Allowed file types:** PDF, PNG, JPEG, JSON, XML, TXT, CSV
                    **Max file size:** 100MB
                    
                    **Required permissions:** `files.write` scope OR `users`/`admins` group membership
                    
                    **Idempotency:** Send `X-Idempotency-Key` header for safe retries
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
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Insufficient permissions",
                    content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Idempotency key conflict - request already in progress",
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
                    responseCode = "429",
                    description = "Rate limit exceeded",
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
    @PreAuthorize("hasAnyAuthority('SCOPE_files.write', 'ROLE_USERS', 'ROLE_ADMINS')")
    @Timed(value = "file.upload", description = "Time taken to upload a file")
    @RateLimiter(name = "fileUpload", fallbackMethod = "uploadFileFallback")
    @Bulkhead(name = "fileUpload", fallbackMethod = "uploadFileFallback")
    @Idempotent(responseType = FileUploadResponseDto.class)
    public ResponseEntity<FileUploadResponseDto> uploadFile(
            @Parameter(description = "File to upload", required = true)
            @RequestParam("file") MultipartFile file,
            
            @Parameter(description = "Optional upload metadata")
            @ModelAttribute FileUploadRequestDto request
    ) throws IOException {
        // Get authenticated user from security context
        UserPrincipal currentUser = currentUserService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User not found in security context"));
        
        log.info("Received upload request: filename={}, size={}, contentType={}, userId={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType(), currentUser.userId());

        UploadFileCommand command = UploadFileCommand.builder()
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .content(file.getInputStream())
                .correlationId(request != null ? request.correlationId() : null)
                .uploadedBy(currentUser.userId())
                .build();

        SecureFile uploadedFile = uploadFileUseCase.execute(command);
        FileUploadResponseDto response = fileMapper.toResponseDto(uploadedFile);

        log.info("Upload successful: fileId={}, status={}, userId={}", 
                uploadedFile.getId(), uploadedFile.getStatus(), currentUser.userId());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/../{fileId}")
                .buildAndExpand(uploadedFile.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Fallback method for rate limiting and bulkhead.
     * Returns 429 Too Many Requests or 503 Service Unavailable.
     */
    @SuppressWarnings("unused")
    private ResponseEntity<FileUploadResponseDto> uploadFileFallback(
            MultipartFile file,
            FileUploadRequestDto request,
            Exception ex) {
        log.warn("Upload fallback triggered: {}", ex.getMessage());
        
        if (ex instanceof io.github.resilience4j.ratelimiter.RequestNotPermitted) {
            throw new RateLimitExceededException("Upload rate limit exceeded. Please try again later.");
        }
        if (ex instanceof io.github.resilience4j.bulkhead.BulkheadFullException) {
            throw new ServiceUnavailableException("Service is currently overloaded. Please try again later.");
        }
        throw new RuntimeException("Upload failed", ex);
    }

    @Operation(
            summary = "Get file metadata",
            description = """
                    Retrieve metadata for a previously uploaded file.
                    
                    **Required permissions:** `files.read` scope OR `users`/`admins` group membership
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "File metadata retrieved",
                    content = @Content(schema = @Schema(implementation = FileUploadResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Insufficient permissions",
                    content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "File not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
            )
    })
    @GetMapping("/{fileId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_files.read', 'ROLE_USERS', 'ROLE_ADMINS')")
    @Timed(value = "file.get", description = "Time taken to get file metadata")
    @RateLimiter(name = "fileDownload")
    public ResponseEntity<FileUploadResponseDto> getFile(
            @Parameter(description = "File ID", required = true)
            @PathVariable String fileId
    ) {
        String userId = currentUserService.getCurrentUserId().orElse("unknown");
        log.info("Get file request: fileId={}, userId={}", fileId, userId);

        SecureFile file = getFileUseCase.getByIdOrThrow(FileId.of(fileId));
        FileUploadResponseDto response = fileMapper.toResponseDto(file);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Delete a file",
            description = """
                    Delete a file from the platform.
                    
                    **Required permissions:** `admins` group membership only
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "File deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin role required",
                    content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "File not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
            )
    })
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
}
