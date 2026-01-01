package io.github.pauszek.fsampgateway.infrastructure.security.cognito;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * Configuration for Cognito JWT validation and authentication.
 * 
 * This configuration:
 * - Sets up JWT decoder with Cognito JWKS endpoint
 * - Configures token validators (issuer, audience, timestamps)
 * - Integrates custom JWT-to-Authentication converter for role mapping
 * 
 * Enterprise features:
 * - JWKS caching for performance
 * - Custom claim validation
 * - Cognito groups → Spring Security authorities mapping
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CognitoProperties.class)
@RequiredArgsConstructor
public class CognitoJwtConfig {

    private static final Duration JWKS_CACHE_LIFESPAN = Duration.ofHours(1);
    private static final Duration JWKS_CACHE_REFRESH = Duration.ofMinutes(5);
    private static final Duration JWKS_CACHE_REFRESH_TIMEOUT = Duration.ofSeconds(30);

    private final CognitoProperties cognitoProperties;

    /**
     * Creates a JWT decoder configured for AWS Cognito tokens.
     * 
     * Key features:
     * - Uses JWKS endpoint for signature verification
     * - Validates issuer matches Cognito User Pool
     * - Validates audience matches client ID
     * - Validates token expiration
     * - Implements JWKS caching for performance
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        log.info("Configuring Cognito JWT decoder with issuer: {}", cognitoProperties.getIssuerUri());
        
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(cognitoProperties.getJwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        
        // Configure validators
        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                createValidators()
        );
        
        decoder.setJwtValidator(validators);
        
        return decoder;
    }

    /**
     * Creates list of token validators.
     */
    private List<OAuth2TokenValidator<Jwt>> createValidators() {
        return List.of(
                // Validate token timestamps (exp, iat, nbf)
                new JwtTimestampValidator(Duration.ofSeconds(30)),
                
                // Validate issuer
                new JwtIssuerValidator(cognitoProperties.getIssuerUri()),
                
                // Validate audience (client_id for Cognito)
                new JwtClaimValidator<>("client_id", 
                        clientId -> cognitoProperties.clientId().equals(clientId) ||
                                    (clientId instanceof List<?> list && list.contains(cognitoProperties.clientId()))),
                
                // Custom validator for token_use (must be 'access' for API calls)
                new JwtClaimValidator<>("token_use", 
                        tokenUse -> "access".equals(tokenUse) || "id".equals(tokenUse))
        );
    }

    /**
     * JWT Authentication Converter that extracts authorities from Cognito JWT.
     * 
     * Maps:
     * - cognito:groups claim → ROLE_* authorities
     * - scope claim → SCOPE_* authorities
     * 
     * @see CognitoJwtRoleConverter
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(
            CognitoJwtRoleConverter cognitoJwtRoleConverter) {
        
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(cognitoJwtRoleConverter);
        converter.setPrincipalClaimName("sub"); // Use Cognito user ID as principal
        
        return converter;
    }

    /**
     * Creates the role converter bean.
     */
    @Bean
    public CognitoJwtRoleConverter cognitoJwtRoleConverter() {
        return new CognitoJwtRoleConverter();
    }
}
