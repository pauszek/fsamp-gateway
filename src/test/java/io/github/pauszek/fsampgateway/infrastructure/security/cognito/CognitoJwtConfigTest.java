package io.github.pauszek.fsampgateway.infrastructure.security.cognito;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CognitoJwtConfig")
class CognitoJwtConfigTest {

    private static final String CLIENT_ID = "test-client";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final CognitoJwtConfig config = new CognitoJwtConfig(new CognitoProperties(
            "us-west-2",
            "us-west-2_testpool",
            CLIENT_ID,
            "https://fsamp-test-api",
            null,
            "http://localhost:4566/us-west-2_testpool/.well-known/jwks.json",
            "http://localhost:4566/us-west-2_testpool"
    ));

    @Test
    @DisplayName("should create JWT decoder with Cognito validators")
    void shouldCreateJwtDecoderWithCognitoValidators() {
        assertThat(config.jwtDecoder()).isNotNull();
    }

    @Test
    @DisplayName("should accept access token client_id")
    void shouldAcceptAccessTokenClientId() {
        var result = config.cognitoClientValidator().validate(jwt("access", CLIENT_ID, List.of()));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("should reject ID token audience")
    void shouldRejectIdTokenAudience() {
        var result = config.cognitoClientValidator().validate(jwt("id", null, List.of(CLIENT_ID)));

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("should reject token for another client")
    void shouldRejectTokenForAnotherClient() {
        var result = config.cognitoClientValidator().validate(jwt(
                "access",
                "other-client",
                List.of("another-audience")
        ));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .extracting(OAuth2Error::getErrorCode)
                .containsExactly("invalid_token");
    }

    private static Jwt jwt(String tokenUse, String clientId, List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-123")
                .claim("token_use", tokenUse)
                .issuer("http://localhost:4566/us-west-2_testpool")
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(3600));

        if (clientId != null) {
            builder.claim("client_id", clientId);
        }
        if (!audience.isEmpty()) {
            builder.audience(audience);
        }

        return builder.build();
    }
}
