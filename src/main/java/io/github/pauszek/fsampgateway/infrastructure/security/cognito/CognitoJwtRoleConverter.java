package io.github.pauszek.fsampgateway.infrastructure.security.cognito;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Locale;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class CognitoJwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String COGNITO_GROUPS_CLAIM = "cognito:groups";
    private static final String SCOPE_CLAIM = "scope";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String SCOPE_PREFIX = "SCOPE_";
    private final CognitoScopeNormalizer scopeNormalizer;

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
                .map(group -> new SimpleGrantedAuthority(ROLE_PREFIX + group.toUpperCase(Locale.ROOT)));
    }

    private Stream<GrantedAuthority> extractScopeAuthorities(Jwt jwt) {
        return scopeNormalizer.extractScopes(jwt.getClaim(SCOPE_CLAIM)).stream()
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
