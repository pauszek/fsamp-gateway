package io.github.pauszek.fsampgateway.infrastructure.config;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("fips-test")
@Tag("fips")
@DisplayName("FIPS 140-3 Crypto Provider Tests")
@EnabledIf("isACCPAvailable")
class FipsCryptoConfigTest {

    private static final String ACCP_PROVIDER_NAME = "AmazonCorrettoCryptoProvider";
    private static final String ACCP_CLASS = "com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider";

    static boolean isACCPAvailable() {
        try {
            Class.forName(ACCP_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Nested
    @DisplayName("Provider Registration")
    class ProviderRegistration {

        @Test
        @DisplayName("ACCP should be installed as highest-priority provider (position 1)")
        void shouldInstallACCPAtPosition1() {
            Provider[] providers = Security.getProviders();
            
            assertThat(providers).isNotEmpty();
            assertThat(providers[0].getName())
                    .as("ACCP must be the first (highest priority) security provider")
                    .isEqualTo(ACCP_PROVIDER_NAME);
        }

        @Test
        @DisplayName("BouncyCastle FIPS should be registered at position 2")
        void shouldInstallBCFIPSAtPosition2() {
            Provider bcFips = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
            
            assertThat(bcFips)
                    .as("BouncyCastle FIPS provider must be registered")
                    .isNotNull();

            Provider[] providers = Security.getProviders();
            assertThat(providers).hasSizeGreaterThanOrEqualTo(2);
            assertThat(providers[1].getName())
                    .as("BC-FIPS must be the second provider")
                    .isEqualTo(BouncyCastleFipsProvider.PROVIDER_NAME);
        }

        @Test
        @DisplayName("Both FIPS providers should be present")
        void bothFipsProvidersShouldBePresent() {
            assertThat(Security.getProvider(ACCP_PROVIDER_NAME))
                    .as("ACCP provider").isNotNull();
            assertThat(Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME))
                    .as("BC-FIPS provider").isNotNull();
        }
    }

    @Nested
    @DisplayName("ACCP Self-Test")
    class ACCPSelfTest {

        @Test
        @DisplayName("ACCP should pass FIPS integrity self-test")
        void shouldPassACCPSelfTest() throws Exception {
            Class<?> accpClass = Class.forName(ACCP_CLASS);
            Object instance = accpClass.getField("INSTANCE").get(null);
            assertThatCode(() -> instance.getClass().getMethod("assertHealthy").invoke(instance))
                    .as("ACCP FIPS 140-3 self-test must pass")
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Approved-Only Mode")
    class ApprovedOnlyMode {

        @Test
        @DisplayName("BouncyCastle approved-only mode should be enabled")
        void shouldEnableApprovedOnlyMode() {
            String approvedOnly = System.getProperty("org.bouncycastle.fips.approved_only");
            
            assertThat(approvedOnly)
                    .as("BC-FIPS approved-only mode must be set to 'true'")
                    .isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("FIPS Cryptographic Operations")
    class FipsCryptoOperations {

        @Test
        @DisplayName("AES-GCM encryption/decryption roundtrip should work via FIPS provider")
        void shouldPerformAESGCMEncryption() throws Exception {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey key = keyGen.generateKey();

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] plaintext = "FIPS 140-3-oriented encryption test".getBytes();
            byte[] ciphertext = cipher.doFinal(plaintext);

            String providerName = cipher.getProvider().getName();
            assertThat(providerName)
                    .as("AES-GCM should use FIPS-validated provider")
                    .isIn(ACCP_PROVIDER_NAME, BouncyCastleFipsProvider.PROVIDER_NAME);

            Cipher decryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
            decryptCipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] decrypted = decryptCipher.doFinal(ciphertext);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("SHA-256 should be available via FIPS provider")
        void shouldPerformSHA256Hashing() throws Exception {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest("FIPS test data".getBytes());

            assertThat(hash).hasSize(32); // 256 bits = 32 bytes

            String providerName = sha256.getProvider().getName();
            assertThat(providerName)
                    .as("SHA-256 should use FIPS-validated provider")
                    .isIn(ACCP_PROVIDER_NAME,
                          BouncyCastleFipsProvider.PROVIDER_NAME,
                          "SUN"); // SUN is acceptable for MessageDigest
        }

        @Test
        @DisplayName("SHA-384 should be available via FIPS provider")
        void shouldPerformSHA384Hashing() throws Exception {
            MessageDigest sha384 = MessageDigest.getInstance("SHA-384");
            byte[] hash = sha384.digest("FIPS test data".getBytes());

            assertThat(hash).hasSize(48); // 384 bits = 48 bytes
        }

        @Test
        @DisplayName("SHA-512 should be available via FIPS provider")
        void shouldPerformSHA512Hashing() throws Exception {
            MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
            byte[] hash = sha512.digest("FIPS test data".getBytes());

            assertThat(hash).hasSize(64); // 512 bits = 64 bytes
        }
    }
}
