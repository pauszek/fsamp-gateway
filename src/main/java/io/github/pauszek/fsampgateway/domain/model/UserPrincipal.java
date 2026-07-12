package io.github.pauszek.fsampgateway.domain.model;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public record UserPrincipal(
        String userId,
        String email,
        String name,
        Set<String> groups,
        Set<String> scopes,
        String tenantId,
        Instant tokenIssuedAt,
        Instant tokenExpiresAt
) {

    public boolean hasGroup(String group) {
        return groups != null && groups.contains(group);
    }

    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }

    public boolean isAdmin() {
        return hasGroup("admins");
    }

    public boolean isTokenExpired() {
        return tokenExpiresAt != null && Instant.now().isAfter(tokenExpiresAt);
    }

    public Set<String> getRoles() {
        if (groups == null) {
            return Set.of();
        }
        return groups.stream()
                .map(g -> "ROLE_" + g.toUpperCase())
                .collect(Collectors.toSet());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userId;
        private String email;
        private String name;
        private Set<String> groups = Set.of();
        private Set<String> scopes = Set.of();
        private String tenantId;
        private Instant tokenIssuedAt;
        private Instant tokenExpiresAt;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder groups(Collection<String> groups) {
            this.groups = groups != null ? Set.copyOf(groups) : Set.of();
            return this;
        }

        public Builder scopes(Collection<String> scopes) {
            this.scopes = scopes != null ? Set.copyOf(scopes) : Set.of();
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder tokenIssuedAt(Instant tokenIssuedAt) {
            this.tokenIssuedAt = tokenIssuedAt;
            return this;
        }

        public Builder tokenExpiresAt(Instant tokenExpiresAt) {
            this.tokenExpiresAt = tokenExpiresAt;
            return this;
        }

        public UserPrincipal build() {
            return new UserPrincipal(
                    userId, email, name, groups, scopes,
                    tenantId, tokenIssuedAt, tokenExpiresAt
            );
        }
    }
}
