package io.github.pauszek.fsampgateway.infrastructure.security.cognito;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CognitoJwtRoleConverter")
class CognitoJwtRoleConverterTest {

    private final CognitoJwtRoleConverter converter = new CognitoJwtRoleConverter();

    @Test
    @DisplayName("should extract role authorities from cognito:groups claim")
    void shouldExtractGroupAuthorities() {
        Jwt jwt = createJwt(Map.of(
                "cognito:groups", List.of("admins", "users"),
                "sub", "user-123"
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMINS", "ROLE_USERS");
    }

    @Test
    @DisplayName("should extract scope authorities from space-separated scope claim")
    void shouldExtractScopeAuthoritiesFromString() {
        Jwt jwt = createJwt(Map.of(
                "scope", "openid profile email files.read files.write",
                "sub", "user-123"
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains(
                        "SCOPE_openid",
                        "SCOPE_profile",
                        "SCOPE_email",
                        "SCOPE_files.read",
                        "SCOPE_files.write"
                );
    }

    @Test
    @DisplayName("should extract scope authorities from list scope claim")
    void shouldExtractScopeAuthoritiesFromList() {
        Jwt jwt = createJwt(Map.of(
                "scope", List.of("openid", "files.read"),
                "sub", "user-123"
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("SCOPE_openid", "SCOPE_files.read");
    }

    @Test
    @DisplayName("should combine groups and scopes")
    void shouldCombineGroupsAndScopes() {
        Jwt jwt = createJwt(Map.of(
                "cognito:groups", List.of("users"),
                "scope", "files.read files.write",
                "sub", "user-123"
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "ROLE_USERS",
                        "SCOPE_files.read",
                        "SCOPE_files.write"
                );
    }

    @Test
    @DisplayName("should handle missing cognito:groups claim")
    void shouldHandleMissingGroups() {
        Jwt jwt = createJwt(Map.of(
                "scope", "openid",
                "sub", "user-123"
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_openid");
    }

    @Test
    @DisplayName("should handle missing scope claim")
    void shouldHandleMissingScope() {
        Jwt jwt = createJwt(Map.of(
                "cognito:groups", List.of("admins"),
                "sub", "user-123"
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMINS");
    }

    @Test
    @DisplayName("should ignore unsupported scope claim type")
    void shouldIgnoreUnsupportedScopeClaimType() {
        Jwt jwt = createJwt(Map.of(
                "scope", 42,
                "sub", "user-123"
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("should handle empty claims")
    void shouldHandleEmptyClaims() {
        Jwt jwt = createJwt(Map.of("sub", "user-123"));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("should uppercase group names in roles")
    void shouldUppercaseGroupNames() {
        Jwt jwt = createJwt(Map.of(
                "cognito:groups", List.of("Power_Users", "api-consumers"),
                "sub", "user-123"
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_POWER_USERS", "ROLE_API-CONSUMERS");
    }

    @Test
    @DisplayName("should handle single group as string")
    void shouldHandleSingleGroupAsString() {
        Jwt jwt = createJwt(Map.of(
                "cognito:groups", "admins",  // Some IdPs return single value as string
                "sub", "user-123"
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMINS");
    }

    private Jwt createJwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(claims))
                .build();
    }
}
