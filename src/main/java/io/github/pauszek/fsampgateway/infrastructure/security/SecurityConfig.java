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
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
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

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ROLE_ADMINS = "ADMINS";
    private static final String ROLE_ADMINS_AUTHORITY = "ROLE_" + ROLE_ADMINS;
    private static final String ROLE_USERS_AUTHORITY = "ROLE_USERS";
    private static final String STATELESS_API_PATTERN = "/api/v1/**";
    private static final String FILES_API_PATTERN = "/api/v1/files/**";

    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:local}")
    private String activeProfile;

    @org.springframework.beans.factory.annotation.Value("${security.cors.allowed-origins:}")
    private String configuredOrigins;

    @org.springframework.beans.factory.annotation.Value("${security.cognito.allow-group-fallback:false}")
    private boolean allowGroupFallback;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(SecurityConfig::configureCsrfForStatelessApi)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .headers(headers -> headers
                        .contentSecurityPolicy(csp ->
                                csp.policyDirectives("default-src 'self'; frame-ancestors 'none';"))
                        .xssProtection(xss ->
                                xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer ->
                                referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts ->
                                hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .contentTypeOptions(contentType -> {})
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                        .authenticationEntryPoint(new CognitoAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new CognitoAccessDeniedHandler(objectMapper))
                )

                .authorizeHttpRequests(auth -> {
                        auth.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();

                        if (isDevOrLocal()) {
                            auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                        } else {
                            auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                    .hasRole(ROLE_ADMINS);
                        }

                        auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                        if (allowGroupFallback) {
                            auth.requestMatchers(HttpMethod.POST, FILES_API_PATTERN)
                                    .hasAnyAuthority("SCOPE_files.write", ROLE_USERS_AUTHORITY, ROLE_ADMINS_AUTHORITY);
                            auth.requestMatchers(HttpMethod.GET, FILES_API_PATTERN)
                                    .hasAnyAuthority("SCOPE_files.read", ROLE_USERS_AUTHORITY, ROLE_ADMINS_AUTHORITY);
                            auth.requestMatchers(HttpMethod.DELETE, FILES_API_PATTERN)
                                    .hasAnyAuthority("SCOPE_files.delete", ROLE_ADMINS_AUTHORITY);
                        } else {
                            auth.requestMatchers(HttpMethod.POST, FILES_API_PATTERN)
                                    .hasAuthority("SCOPE_files.write");
                            auth.requestMatchers(HttpMethod.GET, FILES_API_PATTERN)
                                    .hasAuthority("SCOPE_files.read");
                            auth.requestMatchers(HttpMethod.DELETE, FILES_API_PATTERN)
                                    .hasAuthority("SCOPE_files.delete");
                        }

                        auth.requestMatchers("/api/v1/admin/**")
                                .hasRole(ROLE_ADMINS)

                        .requestMatchers(STATELESS_API_PATTERN).authenticated()

                        .anyRequest().denyAll();
                })

                .build();
    }

    @SuppressWarnings("java:S4502")
    private static void configureCsrfForStatelessApi(CsrfConfigurer<HttpSecurity> csrf) {
        csrf.ignoringRequestMatchers(STATELESS_API_PATTERN); // NOSONAR
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        if (isDevOrLocal()) {
            configuration.setAllowedOriginPatterns(List.of(
                    "http://localhost:*",
                    "https://localhost:*"
            ));
        } else {
            if (configuredOrigins != null && !configuredOrigins.isBlank()) {
                configuration.setAllowedOriginPatterns(
                        List.of(configuredOrigins.split(","))
                );
            }
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Correlation-ID",
                "X-Idempotency-Key",
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

    private boolean isDevOrLocal() {
        return "local".equalsIgnoreCase(activeProfile)
                || "dev".equalsIgnoreCase(activeProfile)
                || "test".equalsIgnoreCase(activeProfile);
    }
}
