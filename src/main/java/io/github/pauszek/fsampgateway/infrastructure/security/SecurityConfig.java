package io.github.pauszek.fsampgateway.infrastructure.security;

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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
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
 * - CORS configuration
 * - Role-based access control via Cognito groups
 * 
 * Authorization model:
 * - Public endpoints: health checks, OpenAPI docs
 * - Authenticated endpoints: All /api/v1/** endpoints
 * - Admin endpoints: Management operations require ROLE_ADMINS
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
                        .authenticationEntryPoint(new CognitoAuthenticationEntryPoint())
                        .accessDeniedHandler(new CognitoAccessDeniedHandler())
                )
                
                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Health endpoints - public
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        
                        // Swagger/OpenAPI - public (consider securing in production)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        
                        // OPTIONS requests - public (CORS preflight)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        
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
                        .anyRequest().denyAll()
                )
                
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "https://localhost:*"
                // Add production origins via environment configuration
        ));
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
}
