package io.github.pauszek.fsampgateway.adapter.out.crypto;

import io.github.pauszek.fsampgateway.domain.model.Checksum;
import io.github.pauszek.fsampgateway.domain.model.MimeType;
import io.github.pauszek.fsampgateway.domain.model.ValidationResult;
import io.github.pauszek.fsampgateway.domain.port.out.ContentValidatorPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.Security;
import java.util.HexFormat;

/**
 * Adapter - Content Validator using Apache Tika and BouncyCastle FIPS.
 * 
 * Features:
 * - MIME type detection using Tika
 * - SHA-256 checksum with FIPS provider
 * - Content-type spoofing detection
 */
@Component
@Slf4j
public class TikaContentValidatorAdapter implements ContentValidatorPort {

    private final Tika tika = new Tika();
    private boolean fipsEnabled = false;

    @PostConstruct
    public void init() {
        if (Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME) == null) {
            try {
                Security.addProvider(new BouncyCastleFipsProvider());
                fipsEnabled = true;
                log.info("BouncyCastle FIPS provider registered for content validation");
            } catch (Exception e) {
                log.warn("Failed to register FIPS provider, using default: {}", e.getMessage());
            }
        } else {
            fipsEnabled = true;
        }
    }

    @Override
    public MimeType detectMimeType(InputStream content, String fileName) {
        try {
            String detected = tika.detect(content, fileName);
            log.debug("Detected MIME type: {} for file: {}", detected, fileName);
            return MimeType.of(detected);
        } catch (IOException e) {
            log.warn("Failed to detect MIME type for {}: {}", fileName, e.getMessage());
            return MimeType.of("application/octet-stream");
        }
    }

    @Override
    public ValidationResult validate(InputStream content, MimeType declaredType, String fileName) {
        try {
            // Detect actual MIME type
            String detected = tika.detect(content, fileName);
            MimeType detectedType = MimeType.of(detected);

            log.debug("Validating content: declared={}, detected={}, file={}", 
                    declaredType, detectedType, fileName);

            // Check if detected type is allowed
            if (!detectedType.isAllowed()) {
                return ValidationResult.invalid(detectedType, 
                        "File type '" + detectedType + "' is not allowed");
            }

            // Warn about type mismatch (potential spoofing attempt)
            if (declaredType != null && !declaredType.value().equals(detectedType.value())) {
                log.warn("MIME type mismatch detected: declared={}, actual={}, file={}", 
                        declaredType, detectedType, fileName);
                // We use the detected type, not the declared one
            }

            return ValidationResult.valid(detectedType);

        } catch (IOException e) {
            log.error("Content validation failed for {}: {}", fileName, e.getMessage(), e);
            return ValidationResult.invalid(declaredType, "Failed to validate content: " + e.getMessage());
        }
    }

    @Override
    public Checksum computeChecksum(byte[] content) {
        try {
            MessageDigest digest;
            if (fipsEnabled) {
                digest = MessageDigest.getInstance("SHA-256", BouncyCastleFipsProvider.PROVIDER_NAME);
            } else {
                digest = MessageDigest.getInstance("SHA-256");
            }
            
            byte[] hash = digest.digest(content);
            String hexHash = HexFormat.of().formatHex(hash);
            
            return Checksum.sha256(hexHash);
            
        } catch (Exception e) {
            log.error("Failed to compute checksum: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to compute checksum", e);
        }
    }
}
