package io.github.pauszek.fsampgateway.adapter.out.crypto;

import io.github.pauszek.fsampgateway.domain.model.Checksum;
import io.github.pauszek.fsampgateway.domain.model.MimeType;
import io.github.pauszek.fsampgateway.domain.model.ValidationResult;
import io.github.pauszek.fsampgateway.domain.port.out.ContentValidatorPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
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
 * - SHA-256 checksum with FIPS provider (production) or default provider (local)
 * - Content-type spoofing detection
 */
@Component
@Slf4j
public class TikaContentValidatorAdapter implements ContentValidatorPort {

    private final Tika tika = new Tika();
    private boolean fipsEnabled = false;
    
    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @Value("${fsamp.security.fips-mode:false}")
    private boolean fipsModeEnabled;

    @PostConstruct
    public void init() {
        if (!fipsModeEnabled) {
            log.info("FIPS mode disabled; skipping FIPS provider registration");
            return;
        }

        // Skip FIPS provider for local development (LocalStack doesn't support FIPS)
        if ("local".equals(activeProfile) || "test".equals(activeProfile) || "e2e".equals(activeProfile)) {
            log.info("Skipping FIPS provider for profile: {}", activeProfile);
            return;
        }
        
        try {
            // Dynamically load BouncyCastle FIPS only in production
            Class<?> providerClass = Class.forName("org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider");
            String providerName = (String) providerClass.getField("PROVIDER_NAME").get(null);
            
            if (Security.getProvider(providerName) == null) {
                Security.addProvider((java.security.Provider) providerClass.getDeclaredConstructor().newInstance());
                fipsEnabled = true;
                log.info("BouncyCastle FIPS provider registered for content validation");
            } else {
                fipsEnabled = true;
            }
        } catch (Exception e) {
            log.warn("FIPS provider not available, using default: {}", e.getMessage());
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
    
    private static final String FIPS_PROVIDER_NAME = "BCFIPS";

    @Override
    public Checksum computeChecksum(InputStream content) {
        try {
            MessageDigest digest;
            if (fipsEnabled) {
                digest = MessageDigest.getInstance("SHA-256", FIPS_PROVIDER_NAME);
            } else {
                digest = MessageDigest.getInstance("SHA-256");
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = content.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            byte[] hash = digest.digest();
            String hexHash = HexFormat.of().formatHex(hash);
            
            return Checksum.sha256(hexHash);
            
        } catch (Exception e) {
            log.error("Failed to compute checksum: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to compute checksum", e);
        }
    }
}
