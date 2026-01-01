package io.github.pauszek.fsampgateway.infrastructure.security.cognito;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts AWS Cognito JWT claims to Spring Security authorities.
 * 
 * Cognito JWT structure differs from standard OAuth2:
 * - Groups are in "cognito:groups" claim (array)
 * - Scopes are in "scope" claim (space-separated string)
 * - Custom attributes in "custom:*" claims
 * 
 * Authority mapping:
 * - cognito:groups ["admins", "users"] → ROLE_ADMINS, ROLE_USERS
 * - scope "openid profile files.read" → SCOPE_openid, SCOPE_profile, SCOPE_files.read
 * 
 * This enables:
 * - @PreAuthorize("hasRole('ADMINS')") - group-based
 * - @PreAuthorize("hasAuthority('SCOPE_files.read')") - scope-based
 */
@Slf4j
public class CognitoJwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String COGNITO_GROUPS_CLAIM = "cognito:groups";
    private static final String SCOPE_CLAIM = "scope";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String SCOPE_PREFIX = "SCOPE_";

    /**
     * Extracts authorities from Cognito JWT.
     * 
     * @param jwt The JWT token from Cognito
     * @return Collection of granted authorities
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        log.debug("Converting JWT claims to authorities for subject: {}", jwt.getSubject());
        
        Collection<GrantedAuthority> authorities = Stream.concat(
                extractGroupAuthorities(jwt),
                extractScopeAuthorities(jwt)
        ).collect(Collectors.toSet());
        
        log.debug("Extracted authorities: {}", authorities);
        
        return authorities;
    }

    /**
     * Extracts role authorities from cognito:groups claim.
     * 
     * Groups in Cognito:
     * - "admins" → ROLE_ADMINS
     * - "users" → ROLE_USERS
     * - "power_users" → ROLE_POWER_USERS
     */
    private Stream<GrantedAuthority> extractGroupAuthorities(Jwt jwt) {
        List<String> groups = extractClaim(jwt, COGNITO_GROUPS_CLAIM);
        
        return groups.stream()
                .filter(Objects::nonNull)
                .filter(group -> !group.isBlank())
                .map(group -> new SimpleGrantedAuthority(ROLE_PREFIX + group.toUpperCase()));
    }

    /**
     * Extracts scope authorities from scope claim.
     * 
     * Cognito stores scopes as space-separated string:
     * "openid profile email files.read files.write"
     * 
     * Converts to:
     * - SCOPE_openid
     * - SCOPE_profile
     * - SCOPE_email
     * - SCOPE_files.read
     * - SCOPE_files.write
     */
    private Stream<GrantedAuthority> extractScopeAuthorities(Jwt jwt) {
        Object scopeClaim = jwt.getClaim(SCOPE_CLAIM);
        
        if (scopeClaim == null) {
            return Stream.empty();
        }

        Collection<String> scopes;
        
        if (scopeClaim instanceof String scopeString) {
            // Space-separated scopes (standard OAuth2 format)
            scopes = Arrays.asList(scopeString.split("\\s+"));
        } else if (scopeClaim instanceof Collection<?> scopeCollection) {
            // Array of scopes (some IdPs)
            scopes = scopeCollection.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        } else {
            log.warn("Unexpected scope claim type: {}", scopeClaim.getClass());
            return Stream.empty();
        }
        
        return scopes.stream()
                .filter(Objects::nonNull)
                .filter(scope -> !scope.isBlank())
                .map(scope -> new SimpleGrantedAuthority(SCOPE_PREFIX + scope));
    }

    /**
     * Safely extracts a list claim from JWT.
     * 
     * Handles:
     * - null claims
     * - Single value claims
     * - Array claims
     */
    @SuppressWarnings("unchecked")
    private List<String> extractClaim(Jwt jwt, String claimName) {
        Object claim = jwt.getClaim(claimName);
        
        if (claim == null) {
            return Collections.emptyList();
        }
        
        if (claim instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        
        if (claim instanceof String string) {
            return List.of(string);
        }
        
        log.warn("Unexpected claim type for {}: {}", claimName, claim.getClass());
        return Collections.emptyList();
    }
}
