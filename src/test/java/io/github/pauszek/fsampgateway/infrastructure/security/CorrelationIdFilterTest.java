package io.github.pauszek.fsampgateway.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldGenerateAndExposeCorrelationIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String correlationId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(correlationId).matches("^[0-9a-f-]{36}$");
        assertThat(request.getAttribute(CorrelationIdFilter.REQUEST_CORRELATION_ID))
                .isEqualTo(correlationId);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID)).isNull();
    }

    @Test
    void shouldNormalizeValidCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        request.addHeader(
                CorrelationIdFilter.CORRELATION_ID_HEADER,
                "A1B2C3D4-E5F6-4890-A1B2-C3D4E5F67890"
        );

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo("a1b2c3d4-e5f6-4890-a1b2-c3d4e5f67890");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void shouldRejectMalformedCorrelationIdBeforeInvokingChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "not-a-uuid");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("must be a UUID v4");
        assertThat(chain.getRequest()).isNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID)).isNull();
    }
}
