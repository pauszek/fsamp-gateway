package io.github.pauszek.fsampgateway.infrastructure.security;

import io.github.pauszek.fsampgateway.domain.model.CorrelationId;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Correlation ID Filter.
 * 
 * Ensures every request has a correlation ID for distributed tracing.
 * The correlation ID is:
 * 1. Extracted from X-Correlation-ID header (if provided by client)
 * 2. Generated if not present (32-char hex via {@link CorrelationId#generate()})
 * 3. Added to MDC for structured logging
 * 4. Propagated back in the response header
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_CORRELATION_ID = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            String correlationId = extractOrGenerate(httpRequest);
            MDC.put(MDC_CORRELATION_ID, correlationId);
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    private String extractOrGenerate(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            return CorrelationId.generate().value();
        }
        // Normalize: strip dashes from UUID-formatted correlation IDs
        String normalized = correlationId.replace("-", "").toLowerCase();
        if (normalized.matches("[a-f0-9]{32}")) {
            return normalized;
        }
        // If incoming value doesn't match expected format, generate a new one
        return CorrelationId.generate().value();
    }
}
