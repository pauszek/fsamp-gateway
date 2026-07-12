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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String REQUEST_CORRELATION_ID = CorrelationIdFilter.class.getName() + ".correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            String correlationId;
            try {
                correlationId = extractOrGenerate(httpRequest);
            } catch (IllegalArgumentException e) {
                httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                httpResponse.setContentType("application/problem+json");
                httpResponse.getWriter().write(
                        "{\"type\":\"https://api.fsamp.io/errors/invalid-correlation-id\","
                                + "\"title\":\"Bad Request\",\"status\":400,"
                                + "\"detail\":\"X-Correlation-ID must be a UUID v4\"}"
                );
                return;
            }
            MDC.put(MDC_CORRELATION_ID, correlationId);
            httpRequest.setAttribute(REQUEST_CORRELATION_ID, correlationId);
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
        return CorrelationId.of(correlationId).value();
    }
}
