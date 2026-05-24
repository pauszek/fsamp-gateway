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
            if (approvedOnlyMode) {
                System.setProperty("org.bouncycastle.fips.approved_only", "true");
                log.info("BouncyCastle FIPS approved-only mode enabled");
            }
            if (Security.getProvider(ACCP_PROVIDER_NAME) == null) {
                Class<?> accpClass = Class.forName(ACCP_CLASS);
                Method installMethod = accpClass.getMethod("install");
                installMethod.invoke(null);
                log.info("Amazon Corretto Crypto Provider (ACCP) installed at position 1 — FIPS 140-3 Level 1 TLS");
            } else {
                log.info("ACCP already registered");
            }
            if (Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME) == null) {
                Security.insertProviderAt(new BouncyCastleFipsProvider(), 2);
                log.info("BouncyCastle FIPS provider registered at position 2");
            } else {
                log.info("BouncyCastle FIPS provider already registered");
            }

            if (log.isDebugEnabled()) {
                var providers = Security.getProviders();
                log.debug("Registered security providers ({}):", providers.length);
                for (int i = 0; i < providers.length; i++) {
                    log.debug("  [{}] {} v{}", i + 1, providers[i].getName(), providers[i].getVersionStr());
                }
            }
            verifyFipsMode();
            
        } catch (ReflectiveOperationException | IllegalStateException e) {
            log.error("Failed to initialize FIPS providers", e);
            throw new IllegalStateException("FIPS initialization failed - cannot start in non-FIPS mode", e);
        }
    }

    private void verifyFipsMode() {
        Provider accpProvider = Security.getProvider(ACCP_PROVIDER_NAME);
        if (accpProvider == null) {
            throw new IllegalStateException("ACCP provider not found after installation");
        }

        try {
            Class<?> accpClass = Class.forName(ACCP_CLASS);
            Object instance = accpClass.getField("INSTANCE").get(null);
            Method assertHealthy = instance.getClass().getMethod("assertHealthy");
            assertHealthy.invoke(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ACCP self-test failed", e);
        }
        log.info("ACCP self-test passed — FIPS 140-3 crypto operational");

        Provider bcFipsProvider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (bcFipsProvider == null) {
            throw new IllegalStateException("BouncyCastle FIPS provider not found after registration");
        }

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
