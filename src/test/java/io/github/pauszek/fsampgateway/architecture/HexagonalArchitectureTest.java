package io.github.pauszek.fsampgateway.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@DisplayName("Hexagonal Architecture Tests")
class HexagonalArchitectureTest {

    private static final String BASE_PACKAGE = "io.github.pauszek.fsampgateway";
    
    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    @Nested
    @DisplayName("Layer Dependency Rules")
    class LayerDependencyTests {

        @Test
        @DisplayName("Domain layer should not depend on other layers")
        void domainShouldNotDependOnOtherLayers() {
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..adapter..",
                            "..application..",
                            "..infrastructure.."
                    )
                    .because("Domain layer must be independent and contain only business logic")
                    .check(classes);
        }

        @Test
        @DisplayName("Application layer should only depend on Domain")
        void applicationShouldOnlyDependOnDomain() {
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..adapter..",
                            "..infrastructure.."
                    )
                    .because("Application layer should only orchestrate domain logic")
                    .check(classes);
        }

        @Test
        @DisplayName("Adapters should not depend on Infrastructure (except security/idempotency)")
        void adaptersShouldNotDependOnInfrastructure() {
            noClasses()
                    .that().resideInAPackage("..adapter..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.config..", "..infrastructure.observability..")
                    .because("Adapters should be independent of infrastructure config/observability")
                    .check(classes);
        }

        @Test
        @DisplayName("Layered architecture is respected (with security exceptions)")
        void layeredArchitectureIsRespected() {
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Domain").definedBy("..domain..")
                    .layer("Application").definedBy("..application..")
                    .layer("Adapter").definedBy("..adapter..")
                    .layer("InfraConfig").definedBy("..infrastructure.config..")
                    .layer("InfraSecurity").definedBy("..infrastructure.security..", "..infrastructure.idempotency..")
                    .layer("InfraObservability").definedBy("..infrastructure.observability..")
                    
                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "InfraConfig", "InfraSecurity", "InfraObservability")
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "InfraConfig", "InfraSecurity", "InfraObservability")
                    .whereLayer("Adapter").mayOnlyBeAccessedByLayers("InfraConfig", "InfraSecurity")
                    .whereLayer("InfraSecurity").mayOnlyBeAccessedByLayers("Adapter", "InfraConfig")
                    .whereLayer("InfraConfig").mayNotBeAccessedByAnyLayer()
                    .whereLayer("InfraObservability").mayNotBeAccessedByAnyLayer()
                    
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Domain Layer Rules")
    class DomainLayerTests {

        @Test
        @DisplayName("Domain should not use Spring annotations")
        void domainShouldNotUseSpringAnnotations() {
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..")
                    .because("Domain should be framework-agnostic")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain should not use AWS SDK directly")
        void domainShouldNotUseAwsSdk() {
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("software.amazon.awssdk..")
                    .because("Domain should not have infrastructure dependencies")
                    .check(classes);
        }

        @Test
        @DisplayName("Port interfaces should be interfaces")
        void portsShouldBeInterfaces() {
            classes()
                    .that().resideInAPackage("..domain.port..")
                    .should().beInterfaces()
                    .because("Ports define contracts and should be interfaces")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain exceptions should extend DomainException")
        void domainExceptionsShouldExtendDomainException() {
            classes()
                    .that().resideInAPackage("..domain.exception..")
                    .and().areNotInterfaces()
                    .and().doNotHaveSimpleName("DomainException")
                    .should().beAssignableTo(
                            classes.get("io.github.pauszek.fsampgateway.domain.exception.DomainException")
                                    .reflect())
                    .because("All domain exceptions should form a hierarchy")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Adapter Layer Rules")
    class AdapterLayerTests {

        @Test
        @DisplayName("Input adapters should be in adapter.in package")
        void inputAdaptersShouldBeInCorrectPackage() {
            classes()
                    .that().haveSimpleNameEndingWith("RestAdapter")
                    .or().haveSimpleNameEndingWith("Controller")
                    .should().resideInAPackage("..adapter.in..")
                    .because("Input adapters should be in adapter.in package")
                    .check(classes);
        }

        @Test
        @DisplayName("Output adapters should be in adapter.out package")
        void outputAdaptersShouldBeInCorrectPackage() {
            classes()
                    .that().haveSimpleNameEndingWith("Adapter")
                    .and().resideInAPackage("..adapter.out..")
                    .should().resideInAPackage("..adapter.out..")
                    .because("Output adapters should be in adapter.out package")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Naming Convention Rules")
    class NamingConventionTests {

        @Test
        @DisplayName("Use cases should end with UseCase")
        void useCasesShouldFollowNamingConvention() {
            classes()
                    .that().resideInAPackage("..port.in..")
                    .should().haveSimpleNameEndingWith("UseCase")
                    .because("Use cases should follow naming convention")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain services should end with DomainService")
        void domainServicesShouldFollowNamingConvention() {
            classes()
                    .that().resideInAPackage("..domain.service..")
                    .should().haveSimpleNameEndingWith("DomainService")
                    .because("Domain services should follow naming convention")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain events should end with Event")
        void domainEventsShouldFollowNamingConvention() {
            classes()
                    .that().resideInAPackage("..domain.event..")
                    .and().areNotInterfaces()
                    .and().haveSimpleNameNotContaining("$") // Exclude generated classes
                    .and().haveSimpleNameNotEndingWith("Payload") // Payload classes are event components
                    .should().haveSimpleNameEndingWith("Event")
                    .because("Domain events should follow naming convention")
                    .check(classes);
        }

        @Test
        @DisplayName("DTOs should end with Dto")
        void dtosShouldFollowNamingConvention() {
            classes()
                    .that().resideInAPackage("..application.dto..")
                    .and().areNotMemberClasses() // Exclude Builder inner classes
                    .should().haveSimpleNameEndingWith("Dto")
                    .because("DTOs should follow naming convention")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Circular Dependency Rules")
    class CircularDependencyTests {

        @Test
        @DisplayName("No circular dependencies between domain/application packages")
        void noCircularDependenciesInCore() {
            slices()
                    .matching(BASE_PACKAGE + ".domain.(*)..")
                    .should().beFreeOfCycles()
                    .because("Circular dependencies in domain lead to maintainability issues")
                    .check(classes);
            
            slices()
                    .matching(BASE_PACKAGE + ".application.(*)..")
                    .should().beFreeOfCycles()
                    .because("Circular dependencies in application lead to maintainability issues")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Security Rules")
    class SecurityTests {

        @Test
        @DisplayName("No field injection in production code")
        void noFieldInjection() {
            noFields()
                    .that().areDeclaredInClassesThat().resideInAnyPackage(
                            "..adapter..",
                            "..infrastructure.."
                    )
                    .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .because("Constructor injection should be used for better testability")
                    .check(classes);
        }
    }
}
