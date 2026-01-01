package io.github.pauszek.fsampgateway.infrastructure.security.cognito;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for AWS Cognito integration.
 * 
 * Maps to application.yml under 'cognito' prefix.
 * 
 * @see <a href="https://docs.aws.amazon.com/cognito/latest/developerguide/">AWS Cognito Documentation</a>
 */
@Validated
@ConfigurationProperties(prefix = "cognito")
public record CognitoProperties(
        
        /**
         * AWS region where Cognito User Pool is deployed.
         */
        @NotBlank(message = "Cognito region is required")
        String region,
        
        /**
         * Cognito User Pool ID.
         */
        @NotBlank(message = "Cognito userPoolId is required")
        String userPoolId,
        
        /**
         * Cognito App Client ID for web application.
         */
        @NotBlank(message = "Cognito clientId is required")
        String clientId,
        
        /**
         * Optional: Cognito App Client Secret for confidential clients.
         * Leave null for public clients (SPA).
         */
        String clientSecret,
        
        /**
         * Optional: Custom JWKS endpoint for LocalStack testing.
         * If not provided, derived from region and userPoolId.
         */
        String jwksEndpoint,
        
        /**
         * Optional: Custom issuer URI for LocalStack testing.
         * If not provided, derived from region and userPoolId.
         */
        String issuerUri
) {
    
    /**
     * Gets the JWKS URI for token signature validation.
     * LocalStack requires custom endpoint, AWS uses standard format.
     */
    public String getJwksUri() {
        if (jwksEndpoint != null && !jwksEndpoint.isBlank()) {
            return jwksEndpoint;
        }
        return String.format(
                "https://cognito-idp.%s.amazonaws.com/%s/.well-known/jwks.json",
                region,
                userPoolId
        );
    }
    
    /**
     * Gets the issuer URI for token validation.
     * LocalStack requires custom endpoint, AWS uses standard format.
     */
    public String getIssuerUri() {
        if (issuerUri != null && !issuerUri.isBlank()) {
            return issuerUri;
        }
        return String.format(
                "https://cognito-idp.%s.amazonaws.com/%s",
                region,
                userPoolId
        );
    }
    
    /**
     * Gets the User Pool domain for OAuth2 flows.
     */
    public String getUserPoolDomain() {
        return String.format(
                "https://%s.auth.%s.amazoncognito.com",
                userPoolId.split("_")[1].toLowerCase(),
                region
        );
    }
    
    /**
     * Gets the token endpoint for OAuth2 flows.
     */
    public String getTokenEndpoint() {
        return getUserPoolDomain() + "/oauth2/token";
    }
    
    /**
     * Gets the authorization endpoint for OAuth2 flows.
     */
    public String getAuthorizationEndpoint() {
        return getUserPoolDomain() + "/oauth2/authorize";
    }
}
