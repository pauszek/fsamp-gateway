package io.github.pauszek.fsampgateway.infrastructure.security.cognito;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public final class CognitoScopeNormalizer {

    private final String resourceServerPrefix;

    public CognitoScopeNormalizer(String resourceServerIdentifier) {
        if (resourceServerIdentifier == null || resourceServerIdentifier.isBlank()) {
            throw new IllegalArgumentException("Cognito resource server identifier is required");
        }
        String normalizedIdentifier = resourceServerIdentifier.trim();
        this.resourceServerPrefix = normalizedIdentifier.endsWith("/")
                ? normalizedIdentifier
                : normalizedIdentifier + "/";
    }

    public Set<String> extractScopes(Object scopeClaim) {
        Stream<String> scopes = switch (scopeClaim) {
            case null -> Stream.empty();
            case String scopeString -> Arrays.stream(scopeString.split("\\s+"));
            case Collection<?> scopeCollection -> scopeCollection.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast);
            default -> {
                log.warn("Unexpected scope claim type: {}", scopeClaim.getClass());
                yield Stream.empty();
            }
        };

        return scopes
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .map(this::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalize(String scope) {
        return scope.startsWith(resourceServerPrefix)
                ? scope.substring(resourceServerPrefix.length())
                : scope;
    }
}
