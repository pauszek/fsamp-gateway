package io.github.pauszek.fsampgateway.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security Configuration.
 * 
 * Security measures implemented:
 * - CSRF disabled (stateless REST API)
 * - Session stateless (JWT-ready)
 * - Security headers (CSP, XSS, etc.)
 * - CORS configuration
 * 
 * TODO: Add OAuth2/JWT authentication for production
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF for stateless REST API
                .csrf(AbstractHttpConfigurer::disable)
                
                // Stateless session management
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
                        // HSTS (only in production with HTTPS)
                        .httpStrictTransportSecurity(hsts -> 
                                hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        // Prevent MIME type sniffing
                        .contentTypeOptions(contentType -> {})
                )
                
                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Health endpoints - public
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Swagger/OpenAPI - public (disable in production)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // API endpoints - authenticated (currently permitAll for development)
                        .requestMatchers("/api/v1/**").permitAll()
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
                // Add production origins here
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Correlation-ID",
                "X-Requested-With"
        ));
        configuration.setExposedHeaders(List.of(
                "X-Correlation-ID",
                "X-Request-ID"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
