package io.github.pauszek.fsampgateway.infrastructure.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pauszek.fsampgateway.infrastructure.security.cognito.CurrentUserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IdempotencyAspect")
class IdempotencyAspectTest {

    private final IdempotencyKeyService idempotencyKeyService = mock(IdempotencyKeyService.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IdempotencyAspect aspect = new IdempotencyAspect(
            idempotencyKeyService,
            currentUserService,
            objectMapper
    );

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnCachedStatusHeadersAndBodyForTheSameRequest() throws Throwable {
        installRequest("idem-123");
        String cachedResponse = objectMapper.writeValueAsString(Map.of(
                "statusCode", 202,
                "headers", Map.of("X-Test", List.of("cached")),
                "body", objectMapper.writeValueAsString(Map.of("status", "accepted"))
        ));
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of("user-123"));
        when(idempotencyKeyService.acquireKey(eq("idem-123"), eq("user-123"), anyString()))
                .thenReturn(IdempotencyKeyService.Acquisition.cached(
                        new IdempotencyKeyService.IdempotencyRecord(
                                "idem-123",
                                "user-123",
                                IdempotencyKeyService.KeyStatus.COMPLETED,
                                "fingerprint",
                                "owner-token",
                                cachedResponse,
                                Instant.now()
                        )));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.responseType()).thenReturn((Class) Map.class);

        Object result = aspect.handleIdempotency(joinPoint, idempotent);

        assertThat(result).isInstanceOfSatisfying(ResponseEntity.class, response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(response.getBody()).isEqualTo(Map.of("status", "accepted"));
            assertThat(response.getHeaders().get("X-Test")).isEqualTo(List.of("cached"));
        });
        verify(joinPoint, never()).proceed();
        verify(idempotencyKeyService, never()).completeKey(any(), any(), any(), any());
    }

    @Test
    void shouldPersistTheFullSuccessfulResponseUnderItsLease() throws Throwable {
        installRequest("idem-456");
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of("user-456"));
        when(idempotencyKeyService.acquireKey(eq("idem-456"), eq("user-456"), anyString()))
                .thenReturn(IdempotencyKeyService.Acquisition.acquired("owner-token"));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        ResponseEntity<Map<String, String>> original = ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, "/api/v1/files/123")
                .body(Map.of("fileId", "123"));
        when(joinPoint.proceed()).thenReturn(original);
        Idempotent idempotent = mock(Idempotent.class);

        Object result = aspect.handleIdempotency(joinPoint, idempotent);

        assertThat(result).isSameAs(original);
        verify(idempotencyKeyService).completeKey(
                eq("idem-456"),
                eq("user-456"),
                eq("owner-token"),
                org.mockito.ArgumentMatchers.contains("Location")
        );
    }

    private static void installRequest(String idempotencyKey) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/files/upload");
        request.addHeader(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
