package io.github.pauszek.fsampgateway.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Slf4j
public class CognitoAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CognitoAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, 
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        
        log.warn("Authentication failed for request to {}: {}", 
                request.getRequestURI(), 
                authException.getMessage());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        Map<String, Object> errorResponse = buildErrorResponse(authException, request);
        
        String wwwAuthenticate = buildWwwAuthenticateHeader(authException);
        response.setHeader("WWW-Authenticate", wwwAuthenticate);
        
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }

    private Map<String, Object> buildErrorResponse(AuthenticationException authException, 
                                                    HttpServletRequest request) {
        String errorCode = "unauthorized";
        String errorDescription = "Authentication required";
        
        if (authException instanceof OAuth2AuthenticationException oauth2Exception) {
            OAuth2Error error = oauth2Exception.getError();
            errorCode = error.getErrorCode();
            errorDescription = error.getDescription() != null 
                    ? error.getDescription() 
                    : mapErrorCodeToDescription(errorCode);
        }
        
        return Map.of(
                "type", "https://datatracker.ietf.org/doc/html/rfc6750#section-3.1",
                "title", "Unauthorized",
                "status", HttpStatus.UNAUTHORIZED.value(),
                "detail", errorDescription,
                "instance", request.getRequestURI(),
                "error", errorCode,
                "timestamp", Instant.now().toString()
        );
    }

    private String buildWwwAuthenticateHeader(AuthenticationException authException) {
        StringBuilder header = new StringBuilder("Bearer");
        
        if (authException instanceof OAuth2AuthenticationException oauth2Exception) {
            OAuth2Error error = oauth2Exception.getError();
            header.append(" error=\"").append(error.getErrorCode()).append("\"");
            
            if (error.getDescription() != null) {
                header.append(", error_description=\"")
                      .append(error.getDescription().replace("\"", "\\\""))
                      .append("\"");
            }
        }
        
        return header.toString();
    }

    private String mapErrorCodeToDescription(String errorCode) {
        return switch (errorCode) {
            case "invalid_token" -> "The access token is invalid or has expired";
            case "insufficient_scope" -> "The access token lacks required scope";
            case "invalid_request" -> "The request is missing required parameters";
            default -> "Authentication is required to access this resource";
        };
    }
}
