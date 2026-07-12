package io.github.pauszek.fsampgateway.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeSecurityGuardTest {

    @Test
    void shouldAcceptRequiredProductionSecurityPosture() {
        RuntimeSecurityGuard guard = new RuntimeSecurityGuard(true, true, true);

        assertThatCode(guard::verifySecurityPosture).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "false,true,true",
            "true,false,true",
            "true,true,false"
    })
    void shouldRejectAnyDisabledProductionSecurityControl(
            boolean fipsMode,
            boolean approvedOnly,
            boolean fipsEndpoints
    ) {
        RuntimeSecurityGuard guard = new RuntimeSecurityGuard(
                fipsMode,
                approvedOnly,
                fipsEndpoints
        );

        assertThatThrownBy(guard::verifySecurityPosture)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires FIPS mode");
    }
}
