package io.github.pauszek.fsampgateway.domain.model;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Domain model representing authenticated user principal.
 * 
 * Extracted from Cognito JWT and made available throughout the application.
 * This is a domain model, not tied to Spring Security directly.
 * 
 * @param userId Unique Cognito user ID (sub claim)
 * @param email User's email address
 * @param name User's display name (optional)
 * @param groups Cognito groups the user belongs to
 * @param scopes OAuth2 scopes granted to the token
 * @param tenantId Optional tenant ID for multi-tenancy (custom:tenant_id)
 * @param tokenIssuedAt When the token was issued
 * @param tokenExpiresAt When the token expires
 */
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
    
    /**
     * Checks if user belongs to a specific group.
     */
    public boolean hasGroup(String group) {
        return groups != null && groups.contains(group);
    }
    
    /**
     * Checks if user has a specific scope.
     */
    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }
    
    /**
     * Checks if user is an admin.
     */
    public boolean isAdmin() {
        return hasGroup("admins");
    }
    
    /**
     * Checks if the token is expired.
     */
    public boolean isTokenExpired() {
        return tokenExpiresAt != null && Instant.now().isAfter(tokenExpiresAt);
    }
    
    /**
     * Returns roles as Spring Security expects (ROLE_ prefix).
     */
    public Set<String> getRoles() {
        if (groups == null) {
            return Set.of();
        }
        return groups.stream()
                .map(g -> "ROLE_" + g.toUpperCase())
                .collect(Collectors.toSet());
    }
    
    /**
     * Builder for UserPrincipal.
     */
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
