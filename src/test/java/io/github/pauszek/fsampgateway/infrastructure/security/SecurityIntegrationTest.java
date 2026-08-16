package io.github.pauszek.fsampgateway.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("Security Integration Tests")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String VALID_TOKEN = "valid.jwt.token";

    @Test
    @DisplayName("should allow access to health endpoint without authentication")
    void shouldAllowHealthEndpointWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should allow access to swagger without authentication")
    void shouldAllowSwaggerWithoutAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should reject unauthenticated request to API endpoint")
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/files/123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    @DisplayName("should allow authenticated user with files.read scope")
    void shouldAllowAuthenticatedUserWithReadScope() throws Exception {
        Jwt jwt = createJwt("user-123", List.of("users"), "openid files.read");
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        mockMvc.perform(get("/api/v1/files/" + java.util.UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound()); // File doesn't exist, but auth passed
    }

    @Test
    @DisplayName("should allow the Terraform-shaped Cognito resource server scope")
    void shouldAllowConfiguredResourceServerScope() throws Exception {
        Jwt jwt = createJwt(
                "service-123",
                List.of(),
                "https://fsamp-test-api/files.read"
        );
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        mockMvc.perform(get("/api/v1/files/" + java.util.UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should allow user with ROLE_USERS to access files")
    void shouldAllowUserWithRoleUsers() throws Exception {
        Jwt jwt = createJwt("user-123", List.of("users"), "openid");
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        mockMvc.perform(get("/api/v1/files/" + java.util.UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound()); // File doesn't exist, but auth passed
    }

    @Test
    @DisplayName("should deny delete operation for non-admin user")
    void shouldDenyDeleteForNonAdmin() throws Exception {
        Jwt jwt = createJwt("user-123", List.of("users"), "openid files.write");
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        mockMvc.perform(delete("/api/v1/files/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should deny delete scope without the admins group")
    void shouldDenyDeleteScopeForNonAdmin() throws Exception {
        Jwt jwt = createJwt("user-123", List.of("users"), "openid files.delete");
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        mockMvc.perform(delete("/api/v1/files/" + java.util.UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should allow delete operation for admin user")
    void shouldAllowDeleteForAdmin() throws Exception {
        Jwt jwt = createJwt("admin-123", List.of("admins"), "openid");
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        mockMvc.perform(delete("/api/v1/files/" + java.util.UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound()); // File doesn't exist, but auth passed
    }

    @Test
    @DisplayName("should reject expired token")
    void shouldRejectExpiredToken() throws Exception {
        Jwt jwt = Jwt.withTokenValue("expired-token")
                .header("alg", "RS256")
                .subject("user-123")
                .claim("client_id", "test-client")
                .claim("token_use", "access")
                .issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600)) // Expired 1 hour ago
                .build();

        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        mockMvc.perform(get("/api/v1/files/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should deny access without required scope or role")
    void shouldDenyAccessWithoutRequiredPermissions() throws Exception {
        Jwt jwt = createJwt("user-123", List.of(), "openid email");
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        mockMvc.perform(get("/api/v1/files/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should handle CORS preflight requests")
    void shouldHandleCorsPreflightRequests() throws Exception {
        mockMvc.perform(options("/api/v1/files/upload")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
    }

    private Jwt createJwt(String subject, List<String> groups, String scope) {
        return Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "RS256")
                .header("typ", "JWT")
                .subject(subject)
                .claim("client_id", "test-client")
                .claim("token_use", "access")
                .claim("cognito:groups", groups)
                .claim("scope", scope)
                .claim("email", subject + "@example.com")
                .issuer("https://cognito-idp.us-west-2.amazonaws.com/test-pool")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
