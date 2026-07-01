package io.github.pauszek.fsampgateway.infrastructure.security.cognito;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class CognitoJwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String COGNITO_GROUPS_CLAIM = "cognito:groups";
    private static final String SCOPE_CLAIM = "scope";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String SCOPE_PREFIX = "SCOPE_";

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

    private Stream<GrantedAuthority> extractGroupAuthorities(Jwt jwt) {
        List<String> groups = extractClaim(jwt, COGNITO_GROUPS_CLAIM);
        
        return groups.stream()
                .filter(Objects::nonNull)
                .filter(group -> !group.isBlank())
                .map(group -> new SimpleGrantedAuthority(ROLE_PREFIX + group.toUpperCase()));
    }

    private Stream<GrantedAuthority> extractScopeAuthorities(Jwt jwt) {
        Object scopeClaim = jwt.getClaim(SCOPE_CLAIM);

        Collection<String> scopes = switch (scopeClaim) {
            case null -> Collections.emptyList();
            case String scopeString -> Arrays.asList(scopeString.split("\\s+"));
            case Collection<?> scopeCollection -> scopeCollection.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
            default -> {
                log.warn("Unexpected scope claim type: {}", scopeClaim.getClass());
                yield Collections.emptyList();
            }
        };
        
        return scopes.stream()
                .filter(Objects::nonNull)
                .filter(scope -> !scope.isBlank())
                .map(scope -> new SimpleGrantedAuthority(SCOPE_PREFIX + scope));
    }

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
