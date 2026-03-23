package io.github.pauszek.fsampgateway.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security Configuration with OAuth2 Resource Server.
 * 
 * Security measures implemented:
 * - OAuth2 Resource Server with JWT validation (AWS Cognito)
 * - CSRF disabled (stateless REST API with Bearer tokens)
 * - Stateless session management
 * - Security headers (CSP, XSS, HSTS, etc.)
 * - CORS configuration (profile-specific origins)
 * - Role-based access control via Cognito groups
 * - Swagger/OpenAPI secured in production (FedRAMP AC-3)
 * 
 * Authorization model:
 * - Public endpoints: health checks
 * - Swagger/OpenAPI: public in local/dev, authenticated in staging/prod
 * - Authenticated endpoints: All /api/v1/** endpoints
 * - Admin endpoints: Management operations require ROLE_ADMINS
 * 
 * FIPS 140-3 Alignment:
 * - HSTS with 1-year max-age and includeSubDomains
 * - CSP restricting frame-ancestors
 * - Referrer-policy: strict-origin-when-cross-origin
 * 
 * @see io.github.pauszek.fsampgateway.infrastructure.security.cognito.CognitoJwtConfig
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;
    private final ObjectMapper objectMapper;
    
    @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:local}")
    private String activeProfile;

    @org.springframework.beans.factory.annotation.Value("${security.cors.allowed-origins:}")
    private String configuredOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF for stateless REST API with Bearer tokens
                .csrf(AbstractHttpConfigurer::disable)
                
                // Stateless session management (no server-side sessions)
                .sessionManagement(session -> 
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // CORS configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // Security headers
                .headers(headers -> headers
                        // Content Security Policy
                        .contentSecurityPolicy(csp -> 
                                csp.policyDirectives("default-src 'self'; frame-ancestors 'none';"))
                        // Prevent XSS
                        .xssProtection(xss -> 
                                xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        // Prevent clickjacking
                        .frameOptions(frame -> frame.deny())
                        // Referrer policy
                        .referrerPolicy(referrer -> 
                                referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // HSTS (in production with HTTPS)
                        .httpStrictTransportSecurity(hsts -> 
                                hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        // Prevent MIME type sniffing
                        .contentTypeOptions(contentType -> {})
                )
                
                // OAuth2 Resource Server configuration
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                        .authenticationEntryPoint(new CognitoAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new CognitoAccessDeniedHandler(objectMapper))
                )
                
                // Authorization rules
                .authorizeHttpRequests(auth -> {
                        // Health endpoints - public
                        auth.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
                        
                        // Swagger/OpenAPI - public in local/dev, authenticated in staging/prod (FedRAMP AC-3)
                        if (isDevOrLocal()) {
                            auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                        } else {
                            auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                    .hasRole("ADMINS");
                        }
                        
                        // OPTIONS requests - public (CORS preflight)
                        auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        
                        // File upload - requires authentication and write scope
                        .requestMatchers(HttpMethod.POST, "/api/v1/files/**")
                                .hasAnyAuthority("SCOPE_files.write", "ROLE_ADMINS", "ROLE_USERS")
                        
                        // File download - requires authentication and read scope
                        .requestMatchers(HttpMethod.GET, "/api/v1/files/**")
                                .hasAnyAuthority("SCOPE_files.read", "ROLE_ADMINS", "ROLE_USERS")
                        
                        // File deletion - admin only
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/files/**")
                                .hasRole("ADMINS")
                        
                        // Admin endpoints - admin role only
                        .requestMatchers("/api/v1/admin/**")
                                .hasRole("ADMINS")
                        
                        // All other API endpoints - authenticated
                        .requestMatchers("/api/v1/**").authenticated()
                        
                        // All other requests denied
                        .anyRequest().denyAll();
                })
                
                .build();
    }

    /**
     * CORS configuration — profile-aware origin restrictions (FedRAMP AC-4).
     *
     * <ul>
     *   <li><b>local / dev</b> — localhost origins for development</li>
     *   <li><b>staging / prod</b> — only the API Gateway domain via {@code cors.allowed-origins} property</li>
     * </ul>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        if (isDevOrLocal()) {
            configuration.setAllowedOriginPatterns(List.of(
                    "http://localhost:*",
                    "https://localhost:*"
            ));
        } else {
            // Production / staging — restrict to explicitly configured origins only
            if (configuredOrigins != null && !configuredOrigins.isBlank()) {
                configuration.setAllowedOriginPatterns(
                        List.of(configuredOrigins.split(","))
                );
            }
            // If no origins configured, CORS will reject all cross-origin requests (safe default)
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Correlation-ID",
                "X-Requested-With",
                "X-XSRF-TOKEN"
        ));
        configuration.setExposedHeaders(List.of(
                "X-Correlation-ID",
                "X-Request-ID",
                "Location"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Returns {@code true} for local and dev profiles where relaxed security is acceptable.
     */
    private boolean isDevOrLocal() {
        return "local".equalsIgnoreCase(activeProfile) 
                || "dev".equalsIgnoreCase(activeProfile)
                || "test".equalsIgnoreCase(activeProfile);
    }
}
