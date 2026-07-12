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
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Slf4j
@Configuration
@EnableConfigurationProperties(CognitoProperties.class)
@RequiredArgsConstructor
public class CognitoJwtConfig {

    private static final Duration JWKS_CACHE_LIFESPAN = Duration.ofHours(1);
    private static final Duration JWKS_CACHE_REFRESH = Duration.ofMinutes(5);
    private static final Duration JWKS_CACHE_REFRESH_TIMEOUT = Duration.ofSeconds(30);

    private final CognitoProperties cognitoProperties;

    @Bean
    public JwtDecoder jwtDecoder() {
        log.info("Configuring Cognito JWT decoder with issuer: {}", cognitoProperties.getIssuerUri());

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(cognitoProperties.getJwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                createValidators()
        );

        decoder.setJwtValidator(validators);

        return decoder;
    }

    private List<OAuth2TokenValidator<Jwt>> createValidators() {
        return List.of(
                new JwtTimestampValidator(Duration.ofSeconds(30)),

                new JwtIssuerValidator(cognitoProperties.getIssuerUri()),

                cognitoClientValidator(),

                new JwtClaimValidator<>("token_use", "access"::equals)
        );
    }

    OAuth2TokenValidator<Jwt> cognitoClientValidator() {
        return jwt -> {
            String expectedClientId = cognitoProperties.clientId();
            String accessTokenClientId = jwt.getClaimAsString("client_id");
            if (expectedClientId.equals(accessTokenClientId)) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "JWT client_id does not match the configured Cognito resource-server client",
                    null
            ));
        };
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(
            CognitoJwtRoleConverter cognitoJwtRoleConverter) {

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(cognitoJwtRoleConverter);
        converter.setPrincipalClaimName("sub"); // Use Cognito user ID as principal

        return converter;
    }

    @Bean
    public CognitoJwtRoleConverter cognitoJwtRoleConverter() {
        return new CognitoJwtRoleConverter();
    }
}
