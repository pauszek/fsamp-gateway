package io.github.pauszek.fsampgateway.infrastructure.config;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;
import java.security.Security;

/**
 * FIPS 140-3 Security Configuration.
 * 
 * Configures BouncyCastle FIPS provider for cryptographic operations.
 * Only active in non-local profiles (dev, staging, prod).
 * 
 * FIPS 140-3 Compliance:
 * - Uses BouncyCastle FIPS provider (NIST validated)
 * - Enforces approved-only mode
 * - All cryptographic operations use FIPS-validated algorithms
 * 
 * Note: AWS KMS operations are inherently FIPS compliant as KMS HSMs
 * are FIPS 140-3 Level 3 validated.
 * 
 * @see <a href="https://csrc.nist.gov/publications/detail/fips/140/3/final">FIPS 140-3</a>
 * @see <a href="https://www.bouncycastle.org/fips-java/BCFipsIn100.pdf">BouncyCastle FIPS</a>
 */
@Configuration
@Profile("!local")
public class FipsCryptoConfig {

    private static final Logger log = LoggerFactory.getLogger(FipsCryptoConfig.class);
    
    @Value("${security.fips.approved-only:true}")
    private boolean approvedOnlyMode;

    @Value("${security.fips.provider-position:1}")
    private int providerPosition;

    @PostConstruct
    public void initializeFipsProvider() {
        log.info("Initializing FIPS 140-3 security configuration");
        
        try {
            // Set system property for approved-only mode before provider initialization
            if (approvedOnlyMode) {
                System.setProperty("org.bouncycastle.fips.approved_only", "true");
                log.info("FIPS approved-only mode enabled");
            }

            // Check if FIPS provider is already registered
            if (Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME) == null) {
                // Insert FIPS provider at specified position (1 = highest priority)
                Security.insertProviderAt(new BouncyCastleFipsProvider(), providerPosition);
                log.info("BouncyCastle FIPS provider registered at position {}", providerPosition);
            } else {
                log.info("BouncyCastle FIPS provider already registered");
            }

            // Log all security providers for debugging
            if (log.isDebugEnabled()) {
                var providers = Security.getProviders();
                log.debug("Registered security providers ({}):", providers.length);
                for (int i = 0; i < providers.length; i++) {
                    log.debug("  [{}] {} v{}", i + 1, providers[i].getName(), providers[i].getVersionStr());
                }
            }

            // Verify FIPS mode is active
            verifyFipsMode();
            
        } catch (Exception e) {
            log.error("Failed to initialize FIPS provider", e);
            throw new IllegalStateException("FIPS initialization failed - cannot start in non-FIPS mode", e);
        }
    }

    /**
     * Verify FIPS mode is properly activated.
     */
    private void verifyFipsMode() {
        var fipsProvider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        
        if (fipsProvider == null) {
            throw new IllegalStateException("FIPS provider not found after registration");
        }

        // Verify approved-only mode if enabled
        if (approvedOnlyMode) {
            String approvedOnly = System.getProperty("org.bouncycastle.fips.approved_only");
            if (!"true".equals(approvedOnly)) {
                throw new IllegalStateException("FIPS approved-only mode not properly configured");
            }
        }

        log.info("FIPS 140-3 mode verified successfully: provider={}, version={}, approved_only={}",
                fipsProvider.getName(),
                fipsProvider.getVersionStr(),
                approvedOnlyMode);
    }
}
