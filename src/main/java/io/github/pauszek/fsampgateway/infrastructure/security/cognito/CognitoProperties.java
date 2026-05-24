package io.github.pauszek.fsampgateway.infrastructure.security.cognito;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cognito")
public record CognitoProperties(
        
        @NotBlank(message = "Cognito region is required")
        String region,
        
        @NotBlank(message = "Cognito userPoolId is required")
        String userPoolId,
        
        @NotBlank(message = "Cognito clientId is required")
        String clientId,
        
        String clientSecret,
        
        String jwksEndpoint,
        
        String issuerUri
) {
    
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
    
    public String getUserPoolDomain() {
        return String.format(
                "https://%s.auth.%s.amazoncognito.com",
                userPoolId.split("_")[1].toLowerCase(),
                region
        );
    }
    
    public String getTokenEndpoint() {
        return getUserPoolDomain() + "/oauth2/token";
    }
    
    public String getAuthorizationEndpoint() {
        return getUserPoolDomain() + "/oauth2/authorize";
    }
}
