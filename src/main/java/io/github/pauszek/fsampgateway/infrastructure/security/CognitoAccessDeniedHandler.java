package io.github.pauszek.fsampgateway.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Slf4j
public class CognitoAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CognitoAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        
        log.warn("Access denied for request to {}: {}", 
                request.getRequestURI(), 
                accessDeniedException.getMessage());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        Map<String, Object> errorResponse = Map.of(
                "type", "https://datatracker.ietf.org/doc/html/rfc7231#section-6.5.3",
                "title", "Forbidden",
                "status", HttpStatus.FORBIDDEN.value(),
                "detail", "You don't have permission to access this resource",
                "instance", request.getRequestURI(),
                "error", "insufficient_scope",
                "timestamp", Instant.now().toString()
        );
        
        response.setHeader("WWW-Authenticate", 
                "Bearer error=\"insufficient_scope\", " +
                "error_description=\"The access token lacks required scope\"");
        
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
