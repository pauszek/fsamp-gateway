package io.github.pauszek.fsampgateway.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

@DisplayName("Security Architecture Tests")
class SecurityArchitectureTest {

    private static final String BASE_PACKAGE = "io.github.pauszek.fsampgateway";
    
    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    @Nested
    @DisplayName("Cryptography Rules")
    class CryptographyTests {

        @Test
        @DisplayName("Only FIPS-approved crypto providers should be used")
        void onlyFipsApprovedCryptoProviders() {
            noClasses()
                    .that().resideInAnyPackage(BASE_PACKAGE + "..")
                    .and().doNotHaveSimpleName("FipsCryptoConfig") // Allow config class
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("javax.crypto.Cipher")
                    .because("Direct Cipher usage bypasses FIPS validation - use BouncyCastle FIPS or AWS KMS")
                    .check(classes);
        }

        @Test
        @DisplayName("No legacy hash algorithms (MD5, SHA-1) should be referenced")
        void noLegacyHashAlgorithms() {
            // Detects the easy footguns where someone calls
            // MessageDigest.getInstance("MD5") or "SHA-1" by string literal.
            // ArchUnit cannot inspect the argument value, so we approximate
            // by forbidding direct use of the unqualified MessageDigest
            // class outside the dedicated FIPS configuration. This keeps
            // hashing centralised on the configured FIPS provider.
            noClasses()
                    .that().resideInAnyPackage(BASE_PACKAGE + "..")
                    .and().doNotHaveSimpleName("FipsCryptoConfig")
                    .and().doNotHaveSimpleName("Checksum")
                    .and().doNotHaveSimpleName("TikaContentValidatorAdapter")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("java.security.MessageDigest")
                    .because("MessageDigest must only be used through the FIPS-approved provider configured at startup; legacy algorithms (MD5, SHA-1) violate FedRAMP SC-13")
                    .check(classes);
        }

        @Test
        @DisplayName("Random sources must be derived from FIPS provider")
        void onlyFipsApprovedRandomSources() {
            noClasses()
                    .that().resideInAnyPackage(BASE_PACKAGE + "..")
                    .and().doNotHaveSimpleName("FipsCryptoConfig")
                    .should().callMethod(java.security.SecureRandom.class, "getInstance", String.class)
                    .because("SecureRandom.getInstance(\"SHA1PRNG\") and similar named instances bypass the FIPS provider; use the no-arg SecureRandom which delegates to the registered FIPS provider")
                    .check(classes);
        }

    }

    @Nested
    @DisplayName("Logging Security Rules")  
    class LoggingSecurityTests {

        @Test
        @DisplayName("No System.out/err usage")
        void noSystemOutUsage() {
            noClasses()
                    .that().resideInAnyPackage(BASE_PACKAGE + "..")
                    .should().accessField(System.class, "out")
                    .orShould().accessField(System.class, "err")
                    .because("Use SLF4J/Logback for structured logging")
                    .check(classes);
        }

        @Test
        @DisplayName("No printStackTrace calls")
        void noPrintStackTrace() {
            noClasses()
                    .that().resideInAnyPackage(BASE_PACKAGE + "..")
                    .should().callMethod(Throwable.class, "printStackTrace")
                    .because("printStackTrace leaks stack traces - use proper logging")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Input Validation Rules")
    class InputValidationTests {

        @Test
        @DisplayName("Controllers should use validation annotations")
        void controllersShouldUseValidation() {
            classes()
                    .that().resideInAPackage("..adapter.in.web..")
                    .and().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("jakarta.validation..", "org.springframework.validation..")
                    .allowEmptyShould(true) // Allow if no classes match
                    .because("All REST inputs must be validated")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Exception Handling Rules")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("No generic Exception throws in domain")
        void noGenericExceptionInDomain() {
            noMethods()
                    .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
                    .should().declareThrowableOfType(Exception.class)
                    .allowEmptyShould(true) // Allow if no classes match
                    .because("Domain should throw specific domain exceptions")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Dependency Injection Rules")
    class DependencyInjectionTests {

        @Test
        @DisplayName("Services should use constructor injection")
        void servicesUseConstructorInjection() {
            noFields()
                    .that().areDeclaredInClassesThat().resideInAnyPackage(
                            "..adapter..",
                            "..application.."
                    )
                    .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .because("Use constructor injection for better testability")
                    .check(classes);
        }
    }
}
