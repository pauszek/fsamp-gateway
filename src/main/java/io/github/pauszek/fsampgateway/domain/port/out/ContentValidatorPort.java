package io.github.pauszek.fsampgateway.domain.port.out;

import io.github.pauszek.fsampgateway.domain.model.Checksum;
import io.github.pauszek.fsampgateway.domain.model.MimeType;
import io.github.pauszek.fsampgateway.domain.model.ValidationResult;

import java.io.InputStream;

/**
 * Secondary Port (Driven) - Content Validator.
 * 
 * This is the interface for file content validation.
 * Implementation is in the adapter layer (e.g., Tika adapter).
 */
public interface ContentValidatorPort {

    /**
     * Detect the actual MIME type of content.
     *
     * @param content  file content
     * @param fileName original filename (for extension-based hints)
     * @return detected MIME type
     */
    MimeType detectMimeType(InputStream content, String fileName);

    /**
     * Validate that content matches the declared MIME type.
     *
     * @param content      file content
     * @param declaredType declared MIME type
     * @param fileName     original filename
     * @return validation result
     */
    ValidationResult validate(InputStream content, MimeType declaredType, String fileName);

    /**
     * Compute checksum of content.
     *
     * @param content file content
     * @return SHA-256 checksum
     */
    Checksum computeChecksum(byte[] content);
}
