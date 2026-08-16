package io.github.pauszek.fsampgateway.infrastructure.security.cognito;

import io.github.pauszek.fsampgateway.domain.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentUserService {

    private static final String COGNITO_GROUPS_CLAIM = "cognito:groups";
    private static final String SCOPE_CLAIM = "scope";
    private static final String EMAIL_CLAIM = "email";
    private static final String NAME_CLAIM = "name";
    private static final String TENANT_ID_CLAIM = "custom:tenant_id";
    private final CognitoScopeNormalizer scopeNormalizer;

    public Optional<UserPrincipal> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(extractUserPrincipal(jwtAuth.getToken()));
        }

        log.warn("Unexpected authentication type: {}", authentication.getClass());
        return Optional.empty();
    }

    public Optional<String> getCurrentUserId() {
        return getCurrentUser().map(UserPrincipal::userId);
    }

    public boolean hasGroup(String group) {
        return getCurrentUser()
                .map(user -> user.hasGroup(group))
                .orElse(false);
    }

    public boolean hasScope(String scope) {
        return getCurrentUser()
                .map(user -> user.hasScope(scope))
                .orElse(false);
    }

    public boolean isAdmin() {
        return hasGroup("admins");
    }

    private UserPrincipal extractUserPrincipal(Jwt jwt) {
        return UserPrincipal.builder()
                .userId(jwt.getSubject())
                .email(jwt.getClaimAsString(EMAIL_CLAIM))
                .name(jwt.getClaimAsString(NAME_CLAIM))
                .groups(extractGroups(jwt))
                .scopes(extractScopes(jwt))
                .tenantId(jwt.getClaimAsString(TENANT_ID_CLAIM))
                .tokenIssuedAt(jwt.getIssuedAt())
                .tokenExpiresAt(jwt.getExpiresAt())
                .build();
    }

    private Set<String> extractGroups(Jwt jwt) {
        Object groups = jwt.getClaim(COGNITO_GROUPS_CLAIM);

        if (groups instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(group -> !group.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }

        return Set.of();
    }

    private Set<String> extractScopes(Jwt jwt) {
        return scopeNormalizer.extractScopes(jwt.getClaim(SCOPE_CLAIM));
    }
}
