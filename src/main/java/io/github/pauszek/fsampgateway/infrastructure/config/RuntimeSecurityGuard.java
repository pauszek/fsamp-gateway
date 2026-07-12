package io.github.pauszek.fsampgateway.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!local & !test & !integration-test")
public class RuntimeSecurityGuard {

    private final boolean fipsMode;
    private final boolean approvedOnly;
    private final boolean fipsEndpoints;

    public RuntimeSecurityGuard(
            @Value("${fsamp.security.fips-mode:false}") boolean fipsMode,
            @Value("${security.fips.approved-only:true}") boolean approvedOnly,
            @Value("${aws.fips-endpoints:true}") boolean fipsEndpoints
    ) {
        this.fipsMode = fipsMode;
        this.approvedOnly = approvedOnly;
        this.fipsEndpoints = fipsEndpoints;
    }

    @PostConstruct
    void verifySecurityPosture() {
        if (!fipsMode || !approvedOnly || !fipsEndpoints) {
            throw new IllegalStateException(
                    "Non-local FSAMP runtime requires FIPS mode, approved-only crypto, and AWS FIPS endpoints");
        }
    }
}
