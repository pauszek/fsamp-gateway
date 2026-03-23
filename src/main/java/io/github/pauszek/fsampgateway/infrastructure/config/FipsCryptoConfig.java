package io.github.pauszek.fsampgateway.infrastructure.config;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.security.Provider;
import java.security.Security;

/**
 * FIPS 140-3 Security Configuration.
 * 
 * Configures a dual-provider FIPS cryptographic stack:
 * <ol>
 *   <li><strong>Amazon Corretto Crypto Provider (ACCP)</strong> — Position 1 (highest priority).
 *       FIPS 140-3 Level 1 validated. Handles TLS, AES-GCM, SHA, ECDSA, RSA.
 *       Uses AWS-LC (libcrypto) which is FIPS validated (CMVP Certificate #4631).</li>
 *   <li><strong>BouncyCastle FIPS</strong> — Position 2. Provides additional FIPS-validated
 *       algorithms not covered by ACCP (e.g., PKCS#12, CMS, additional key agreement schemes).</li>
 * </ol>
 * 
 * Only active in non-local profiles (dev, staging, prod).
 * 
 * <p>FIPS 140-3 Compliance:
 * <ul>
 *   <li>ACCP provides FIPS-validated TLS 1.2/1.3 stack (solves Temurin gap)</li>
 *   <li>BouncyCastle FIPS enforces approved-only mode</li>
 *   <li>All cryptographic operations use NIST-validated algorithms</li>
 *   <li>AWS KMS HSMs are FIPS 140-3 Level 3 validated</li>
 * </ul>
 * 
 * @see <a href="https://github.com/corretto/amazon-corretto-crypto-provider">ACCP GitHub</a>
 * @see <a href="https://csrc.nist.gov/publications/detail/fips/140/3/final">FIPS 140-3</a>
 * @see <a href="https://www.bouncycastle.org/fips-java/BCFipsIn100.pdf">BouncyCastle FIPS</a>
 */
@Configuration
@ConditionalOnProperty(name = "fsamp.security.fips-mode", havingValue = "true")
public class FipsCryptoConfig {

    private static final Logger log = LoggerFactory.getLogger(FipsCryptoConfig.class);
    private static final String ACCP_CLASS = "com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider";
    private static final String ACCP_PROVIDER_NAME = "AmazonCorrettoCryptoProvider";

    @Value("${security.fips.approved-only:true}")
    private boolean approvedOnlyMode;

    @PostConstruct
    public void initializeFipsProvider() {
        log.info("Initializing FIPS 140-3 security configuration (ACCP + BouncyCastle FIPS)");
        
        try {
            // 1. Set BouncyCastle FIPS approved-only mode before provider initialization
            if (approvedOnlyMode) {
                System.setProperty("org.bouncycastle.fips.approved_only", "true");
                log.info("BouncyCastle FIPS approved-only mode enabled");
            }

            // 2. Install ACCP as highest-priority provider (position 1)
            //    ACCP provides FIPS-validated TLS stack and high-performance crypto
            //    Uses reflection to avoid compile-time dependency on platform-specific native library
            if (Security.getProvider(ACCP_PROVIDER_NAME) == null) {
                Class<?> accpClass = Class.forName(ACCP_CLASS);
                Method installMethod = accpClass.getMethod("install");
                installMethod.invoke(null);
                log.info("Amazon Corretto Crypto Provider (ACCP) installed at position 1 — FIPS 140-3 Level 1 TLS");
            } else {
                log.info("ACCP already registered");
            }

            // 3. Register BouncyCastle FIPS as secondary provider (position 2)
            //    BC-FIPS handles algorithms not covered by ACCP
            if (Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME) == null) {
                Security.insertProviderAt(new BouncyCastleFipsProvider(), 2);
                log.info("BouncyCastle FIPS provider registered at position 2");
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

            // 4. Verify FIPS mode is active
            verifyFipsMode();
            
        } catch (Exception e) {
            log.error("Failed to initialize FIPS providers", e);
            throw new IllegalStateException("FIPS initialization failed - cannot start in non-FIPS mode", e);
        }
    }

    /**
     * Verify FIPS mode is properly activated for both providers.
     */
    private void verifyFipsMode() throws Exception {
        // Verify ACCP is installed and healthy
        Provider accpProvider = Security.getProvider(ACCP_PROVIDER_NAME);
        if (accpProvider == null) {
            throw new IllegalStateException("ACCP provider not found after installation");
        }

        // Run ACCP self-test to verify FIPS integrity (via reflection)
        Class<?> accpClass = Class.forName(ACCP_CLASS);
        Object instance = accpClass.getField("INSTANCE").get(null);
        Method assertHealthy = instance.getClass().getMethod("assertHealthy");
        assertHealthy.invoke(instance);
        log.info("ACCP self-test passed — FIPS 140-3 crypto operational");

        // Verify BouncyCastle FIPS
        Provider bcFipsProvider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (bcFipsProvider == null) {
            throw new IllegalStateException("BouncyCastle FIPS provider not found after registration");
        }

        // Verify approved-only mode
        if (approvedOnlyMode) {
            String approvedOnly = System.getProperty("org.bouncycastle.fips.approved_only");
            if (!"true".equals(approvedOnly)) {
                throw new IllegalStateException("FIPS approved-only mode not properly configured");
            }
        }

        log.info("FIPS 140-3 mode verified: ACCP={} (pos 1, TLS+crypto), BC-FIPS={} (pos 2, supplementary), approved_only={}",
                accpProvider.getVersionStr(),
                bcFipsProvider.getVersionStr(),
                approvedOnlyMode);
    }
}
