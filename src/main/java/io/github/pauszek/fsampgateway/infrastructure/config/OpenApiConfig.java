package io.github.pauszek.fsampgateway.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 Configuration.
 * 
 * Provides interactive API documentation via Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(servers())
                .components(components())
                .addSecurityItem(securityRequirement());
    }

    private Info apiInfo() {
        return new Info()
                .title("FSAMP Gateway API")
                .description("""
                        ## Bezpieczna platforma mikroserwisowa w chmurze AWS
                        
                        **FSAMP (File Secure Access & Management Platform)** - Gateway Service
                        
                        ### Features:
                        - 🔐 FIPS 140-3 compliant encryption (AWS KMS)
                        - 📁 Secure file upload with content validation
                        - 🔄 Event-driven architecture (SNS/SQS)
                        - 📊 Full observability (metrics, tracing, logging)
                        - 🛡️ Enterprise security patterns
                        
                        ### Architecture:
                        - Hexagonal Architecture (Ports & Adapters)
                        - Domain-Driven Design (DDD)
                        - Resilience patterns (Circuit Breaker, Retry)
                        
                        ### Thesis Project
                        Master's thesis implementation demonstrating cloud-native microservices
                        with AWS Free Tier and LocalStack for local development.
                        """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("FSAMP Team")
                        .email("contact@fsamp.io")
                        .url("https://github.com/pauszek/fsamp"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    private List<Server> servers() {
        return List.of(
                new Server()
                        .url("http://localhost:8080")
                        .description("Local Development (LocalStack)"),
                new Server()
                        .url("https://api.fsamp.io")
                        .description("Production")
        );
    }

    private Components components() {
        return new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("""
                                AWS Cognito JWT authentication.
                                
                                Obtain a token via:
                                1. Cognito Hosted UI OAuth2 flow
                                2. Cognito API (InitiateAuth)
                                
                                Token contains:
                                - `cognito:groups` - User groups (admins, users)
                                - `scope` - OAuth2 scopes (files.read, files.write)
                                - `sub` - Unique user identifier
                                """));
    }

    private SecurityRequirement securityRequirement() {
        return new SecurityRequirement().addList("bearerAuth");
    }
}
