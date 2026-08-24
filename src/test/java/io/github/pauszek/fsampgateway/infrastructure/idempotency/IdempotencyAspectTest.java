package io.github.pauszek.fsampgateway.infrastructure.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pauszek.fsampgateway.application.dto.FileUploadRequestDto;
import io.github.pauszek.fsampgateway.infrastructure.security.cognito.CurrentUserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IdempotencyAspect")
class IdempotencyAspectTest {

    private static final String OPERATION_ID = "550e8400-e29b-41d4-a716-446655440000";

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
                                OPERATION_ID,
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
            assertThat(response.getHeaders()).containsEntry("X-Test", List.of("cached"));
        });
        verify(joinPoint, never()).proceed();
        verify(idempotencyKeyService, never()).completeKey(any(), any(), any(), any());
    }

    @Test
    void shouldPersistTheFullSuccessfulResponseUnderItsLease() throws Throwable {
        installRequest("idem-456");
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of("user-456"));
        when(idempotencyKeyService.acquireKey(eq("idem-456"), eq("user-456"), anyString()))
                .thenReturn(IdempotencyKeyService.Acquisition.acquired("owner-token", OPERATION_ID));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        ResponseEntity<Map<String, String>> original = ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, "/api/v1/files/123")
                .body(Map.of("fileId", "123"));
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            MockHttpServletRequest request = currentRequest();
            assertThat(request.getAttribute(IdempotencyAspect.OPERATION_ID_ATTRIBUTE))
                    .isEqualTo(OPERATION_ID);
            return original;
        });
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

    @Test
    void shouldProceedWithoutIdempotencyHeaderOrRequestContext() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleIdempotency(joinPoint, mock(Idempotent.class));

        assertThat(result).isEqualTo("result");
        verify(currentUserService, never()).getCurrentUserId();
        verify(idempotencyKeyService, never()).acquireKey(any(), any(), any());
    }

    @Test
    void shouldFingerprintMultipartContentAndKeepSuccessfulResponseWhenCachingFails() throws Throwable {
        installRequest("idem-upload");
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of("user-upload"));
        when(idempotencyKeyService.acquireKey(eq("idem-upload"), eq("user-upload"), anyString()))
                .thenReturn(IdempotencyKeyService.Acquisition.acquired("owner-upload", OPERATION_ID));
        doThrow(new IllegalStateException("DynamoDB unavailable"))
                .when(idempotencyKeyService)
                .completeKey(any(), any(), any(), any());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                new MockMultipartFile(
                        "file",
                        "report.pdf",
                        "application/pdf",
                        "content".getBytes()
                ),
                new FileUploadRequestDto(null, "report", java.util.List.of("audit"))
        });
        ResponseEntity<String> response = ResponseEntity.status(HttpStatus.CREATED).body("created");
        when(joinPoint.proceed()).thenReturn(response);

        Object result = aspect.handleIdempotency(joinPoint, mock(Idempotent.class));

        assertThat(result).isSameAs(response);
        verify(idempotencyKeyService).completeKey(
                eq("idem-upload"),
                eq("user-upload"),
                eq("owner-upload"),
                anyString()
        );
    }

    @Test
    void shouldReleaseLeaseAndPreserveReleaseFailureWhenRequestFails() throws Throwable {
        installRequest("idem-failure");
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of("user-failure"));
        when(idempotencyKeyService.acquireKey(eq("idem-failure"), eq("user-failure"), anyString()))
                .thenReturn(IdempotencyKeyService.Acquisition.acquired("owner-failure", OPERATION_ID));
        IllegalStateException requestFailure = new IllegalStateException("request failed");
        IllegalStateException releaseFailure = new IllegalStateException("release failed");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(requestFailure);
        doThrow(releaseFailure).when(idempotencyKeyService)
                .failKey("idem-failure", "user-failure", "owner-failure");

        Throwable thrown = catchThrowable(
                () -> aspect.handleIdempotency(joinPoint, mock(Idempotent.class))
        );

        assertThat(thrown).isSameAs(requestFailure);
        assertThat(thrown.getSuppressed()).containsExactly(releaseFailure);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectCorruptCachedResponse() throws Throwable {
        installRequest("idem-corrupt");
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of("user-corrupt"));
        when(idempotencyKeyService.acquireKey(eq("idem-corrupt"), eq("user-corrupt"), anyString()))
                .thenReturn(IdempotencyKeyService.Acquisition.cached(
                        new IdempotencyKeyService.IdempotencyRecord(
                                "idem-corrupt",
                                "user-corrupt",
                                IdempotencyKeyService.KeyStatus.COMPLETED,
                                "fingerprint",
                                OPERATION_ID,
                                "owner-corrupt",
                                "not-json",
                                Instant.now()
                        )
                ));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.responseType()).thenReturn((Class) Map.class);

        Throwable thrown = catchThrowable(() -> aspect.handleIdempotency(joinPoint, idempotent));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deserialize");
    }

    private static void installRequest(String idempotencyKey) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/files/upload");
        request.addHeader(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static MockHttpServletRequest currentRequest() {
        return (MockHttpServletRequest) ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
