package io.github.pauszek.fsampgateway.infrastructure.security.cognito;

import io.github.pauszek.fsampgateway.domain.model.UserPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for CurrentUserService.
 */
@DisplayName("CurrentUserService Tests")
@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        currentUserService = new CurrentUserService();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should return empty when not authenticated")
    void shouldReturnEmptyWhenNotAuthenticated() {
        SecurityContextHolder.clearContext();

        Optional<UserPrincipal> result = currentUserService.getCurrentUser();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return empty when authentication is null")
    void shouldReturnEmptyWhenAuthenticationIsNull() {
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(context);

        Optional<UserPrincipal> result = currentUserService.getCurrentUser();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return empty when authentication is not JWT")
    void shouldReturnEmptyWhenAuthenticationIsNotJwt() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        Optional<UserPrincipal> result = currentUserService.getCurrentUser();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should extract user principal from JWT")
    void shouldExtractUserPrincipalFromJwt() {
        Jwt jwt = createJwt(Map.of(
                "sub", "user-123",
                "email", "user@test.com",
                "name", "Test User",
                "cognito:groups", List.of("admins", "users"),
                "scope", "openid files.read files.write"
        ));
        setJwtAuthentication(jwt);

        Optional<UserPrincipal> result = currentUserService.getCurrentUser();

        assertThat(result).isPresent();
        UserPrincipal user = result.get();
        assertThat(user.userId()).isEqualTo("user-123");
        assertThat(user.email()).isEqualTo("user@test.com");
        assertThat(user.name()).isEqualTo("Test User");
        assertThat(user.groups()).containsExactlyInAnyOrder("admins", "users");
        assertThat(user.scopes()).containsExactlyInAnyOrder("openid", "files.read", "files.write");
    }

    @Test
    @DisplayName("should get current user ID")
    void shouldGetCurrentUserId() {
        Jwt jwt = createJwt(Map.of("sub", "user-456"));
        setJwtAuthentication(jwt);

        Optional<String> result = currentUserService.getCurrentUserId();

        assertThat(result).hasValue("user-456");
    }

    @Test
    @DisplayName("should return empty user ID when not authenticated")
    void shouldReturnEmptyUserIdWhenNotAuthenticated() {
        SecurityContextHolder.clearContext();

        Optional<String> result = currentUserService.getCurrentUserId();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should check group membership")
    void shouldCheckGroupMembership() {
        Jwt jwt = createJwt(Map.of(
                "sub", "user-123",
                "cognito:groups", List.of("admins")
        ));
        setJwtAuthentication(jwt);

        assertThat(currentUserService.hasGroup("admins")).isTrue();
        assertThat(currentUserService.hasGroup("users")).isFalse();
    }

    @Test
    @DisplayName("should return false for group when not authenticated")
    void shouldReturnFalseForGroupWhenNotAuthenticated() {
        SecurityContextHolder.clearContext();

        assertThat(currentUserService.hasGroup("admins")).isFalse();
    }

    @Test
    @DisplayName("should check scope")
    void shouldCheckScope() {
        Jwt jwt = createJwt(Map.of(
                "sub", "user-123",
                "scope", "files.read files.write"
        ));
        setJwtAuthentication(jwt);

        assertThat(currentUserService.hasScope("files.read")).isTrue();
        assertThat(currentUserService.hasScope("files.delete")).isFalse();
    }

    @Test
    @DisplayName("should return false for scope when not authenticated")
    void shouldReturnFalseForScopeWhenNotAuthenticated() {
        SecurityContextHolder.clearContext();

        assertThat(currentUserService.hasScope("files.read")).isFalse();
    }

    @Test
    @DisplayName("should check admin status")
    void shouldCheckAdminStatus() {
        Jwt adminJwt = createJwt(Map.of(
                "sub", "admin-123",
                "cognito:groups", List.of("admins")
        ));
        setJwtAuthentication(adminJwt);
        assertThat(currentUserService.isAdmin()).isTrue();

        // Regular user
        Jwt userJwt = createJwt(Map.of(
                "sub", "user-123",
                "cognito:groups", List.of("users")
        ));
        setJwtAuthentication(userJwt);
        assertThat(currentUserService.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("should handle scope as list")
    void shouldHandleScopeAsList() {
        Jwt jwt = createJwt(Map.of(
                "sub", "user-123",
                "scope", List.of("openid", "profile")
        ));
        setJwtAuthentication(jwt);

        Optional<UserPrincipal> result = currentUserService.getCurrentUser();

        assertThat(result).isPresent();
        assertThat(result.get().scopes()).containsExactlyInAnyOrder("openid", "profile");
    }

    @Test
    @DisplayName("should handle missing optional claims")
    void shouldHandleMissingOptionalClaims() {
        Jwt jwt = createJwt(Map.of("sub", "user-123"));
        setJwtAuthentication(jwt);

        Optional<UserPrincipal> result = currentUserService.getCurrentUser();

        assertThat(result).isPresent();
        UserPrincipal user = result.get();
        assertThat(user.userId()).isEqualTo("user-123");
        assertThat(user.email()).isNull();
        assertThat(user.name()).isNull();
        assertThat(user.groups()).isEmpty();
        assertThat(user.scopes()).isEmpty();
    }

    @Test
    @DisplayName("should extract tenant ID from custom claim")
    void shouldExtractTenantId() {
        Jwt jwt = createJwt(Map.of(
                "sub", "user-123",
                "custom:tenant_id", "tenant-abc"
        ));
        setJwtAuthentication(jwt);

        Optional<UserPrincipal> result = currentUserService.getCurrentUser();

        assertThat(result).isPresent();
        assertThat(result.get().tenantId()).isEqualTo("tenant-abc");
    }

    private Jwt createJwt(Map<String, Object> claims) {
        Map<String, Object> allClaims = new HashMap<>(claims);
        // Ensure subject is set
        String subject = (String) allClaims.getOrDefault("sub", "default-user");
        
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(allClaims))
                .build();
    }

    private void setJwtAuthentication(Jwt jwt) {
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
