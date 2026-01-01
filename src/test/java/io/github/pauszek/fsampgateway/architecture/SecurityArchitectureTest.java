package io.github.pauszek.fsampgateway.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * Security-focused Architecture Tests.
 * 
 * Ensures security best practices are enforced at compile time:
 * - No direct logging of sensitive data
 * - Proper exception handling
 * - FIPS compliance patterns
 * 
 * Enterprise Pattern: Security as Code
 */
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
            // Direct javax.crypto.Cipher usage should be avoided in favor of FIPS providers
            // Note: BouncyCastle FIPS and AWS SDK are allowed
            noClasses()
                    .that().resideInAnyPackage(BASE_PACKAGE + "..")
                    .and().doNotHaveSimpleName("FipsCryptoConfig") // Allow config class
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("javax.crypto.Cipher")
                    .because("Direct Cipher usage bypasses FIPS validation - use BouncyCastle FIPS or AWS KMS")
                    .check(classes);
        }

        // Note: MessageDigest is allowed for non-cryptographic purposes like checksums
        // FIPS requires using approved algorithms (SHA-256, SHA-384, SHA-512)
        // The TikaContentValidatorAdapter uses SHA-256 which is FIPS approved
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
            // Controllers in adapter.in.web package should use validation
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
            // Verify no @Autowired on fields
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
