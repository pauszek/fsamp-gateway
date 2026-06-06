package io.github.pauszek.fsampgateway.adapter.out.crypto;

import io.github.pauszek.fsampgateway.domain.model.Checksum;
import io.github.pauszek.fsampgateway.domain.model.FileName;
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

        if ("local".equals(activeProfile) || "test".equals(activeProfile) || "e2e".equals(activeProfile)) {
            log.info("Skipping FIPS provider for profile: {}", activeProfile);
            return;
        }
        
        try {
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
            log.debug("Detected MIME type: {} for file: {}", detected, FileName.redactedForLogs(fileName));
            return MimeType.of(detected);
        } catch (IOException e) {
            log.warn("Failed to detect MIME type for {}: {}", FileName.redactedForLogs(fileName), e.getMessage());
            return MimeType.of("application/octet-stream");
        }
    }

    @Override
    public ValidationResult validate(InputStream content, MimeType declaredType, String fileName) {
        try {
            String detected = tika.detect(content, fileName);
            MimeType detectedType = MimeType.of(detected);

            log.debug("Validating content: declared={}, detected={}, file={}", 
                    declaredType, detectedType, FileName.redactedForLogs(fileName));

            if (!detectedType.isAllowed()) {
                return ValidationResult.invalid(detectedType, 
                        "File type '" + detectedType + "' is not allowed");
            }

            if (declaredType != null && !declaredType.value().equals(detectedType.value())) {
                log.warn("MIME type mismatch detected: declared={}, actual={}, file={}", 
                        declaredType, detectedType, FileName.redactedForLogs(fileName));
            }

            return ValidationResult.valid(detectedType);

        } catch (IOException e) {
            log.error("Content validation failed for {}: {}", FileName.redactedForLogs(fileName), e.getMessage(), e);
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
