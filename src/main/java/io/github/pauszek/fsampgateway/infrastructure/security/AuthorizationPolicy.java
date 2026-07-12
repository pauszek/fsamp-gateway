package io.github.pauszek.fsampgateway.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("authorizationPolicy")
public class AuthorizationPolicy {

    private final boolean groupFallbackAllowed;

    public AuthorizationPolicy(
            @Value("${security.cognito.allow-group-fallback:false}") boolean groupFallbackAllowed
    ) {
        this.groupFallbackAllowed = groupFallbackAllowed;
    }

    public boolean isGroupFallbackAllowed() {
        return groupFallbackAllowed;
    }
}
