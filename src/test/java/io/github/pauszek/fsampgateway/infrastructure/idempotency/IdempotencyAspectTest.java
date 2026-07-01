package io.github.pauszek.fsampgateway.infrastructure.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pauszek.fsampgateway.infrastructure.security.cognito.CurrentUserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
    @DisplayName("should return cached response when idempotency key is completed")
    @SuppressWarnings("unchecked")
    void shouldReturnCachedResponseWhenIdempotencyKeyIsCompleted() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER, "idem-123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String cachedResponse = """
                {"statusCode":202,"body":"{\\"status\\":\\"accepted\\"}"}
                """;
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of("user-123"));
        when(idempotencyKeyService.acquireKey("idem-123", "user-123"))
                .thenReturn(Optional.of(new IdempotencyKeyService.IdempotencyRecord(
                        "idem-123",
                        "user-123",
                        IdempotencyKeyService.KeyStatus.COMPLETED,
                        cachedResponse,
                        Instant.now()
                )));

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("upload");
        when(joinPoint.getSignature()).thenReturn(signature);

        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.responseType()).thenReturn((Class) Map.class);

        Object result = aspect.handleIdempotency(joinPoint, idempotent);

        assertThat(result)
                .isInstanceOfSatisfying(ResponseEntity.class, response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                    assertThat(response.getBody()).isEqualTo(Map.of("status", "accepted"));
                });
        verify(joinPoint, never()).proceed();
        verify(idempotencyKeyService, never()).completeKey(any(), any(), any());
    }
}
