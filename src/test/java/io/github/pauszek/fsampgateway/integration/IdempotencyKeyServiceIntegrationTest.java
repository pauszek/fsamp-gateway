package io.github.pauszek.fsampgateway.integration;

import io.github.pauszek.fsampgateway.infrastructure.idempotency.IdempotencyKeyService;
import io.github.pauszek.fsampgateway.infrastructure.idempotency.IdempotencyKeyService.IdempotencyRecord;
import io.github.pauszek.fsampgateway.infrastructure.idempotency.IdempotencyKeyService.KeyStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("IdempotencyKeyService Integration Tests")
class IdempotencyKeyServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IdempotencyKeyService idempotencyKeyService;

    @Nested
    @DisplayName("acquireKey")
    class AcquireKey {

        @Test
        @DisplayName("should acquire new idempotency key successfully")
        void shouldAcquireNewIdempotencyKeySuccessfully() {
            String idempotencyKey = "idem-" + UUID.randomUUID();
            String userId = "user-" + UUID.randomUUID();

            Optional<IdempotencyRecord> result = idempotencyKeyService.acquireKey(idempotencyKey, userId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return existing record for duplicate key after completion")
        void shouldReturnExistingRecordForDuplicateKey() {
            String idempotencyKey = "idem-duplicate-" + UUID.randomUUID();
            String userId = "user-" + UUID.randomUUID();
            
            Optional<IdempotencyRecord> firstResult = idempotencyKeyService.acquireKey(idempotencyKey, userId);
            assertThat(firstResult).isEmpty(); // First acquisition succeeds
            
            idempotencyKeyService.completeKey(idempotencyKey, userId, "{\"fileId\":\"123\"}");

            Optional<IdempotencyRecord> result = idempotencyKeyService.acquireKey(idempotencyKey, userId);

            assertThat(result).isPresent();
            assertThat(result.get().idempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(result.get().status()).isEqualTo(KeyStatus.COMPLETED);
            assertThat(result.get().response()).isEqualTo("{\"fileId\":\"123\"}");
        }

        @Test
        @DisplayName("should handle null idempotency key gracefully")
        void shouldHandleNullIdempotencyKeyGracefully() {
            String userId = "user-" + UUID.randomUUID();

            Optional<IdempotencyRecord> result = idempotencyKeyService.acquireKey(null, userId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should handle blank idempotency key gracefully")
        void shouldHandleBlankIdempotencyKeyGracefully() {
            String userId = "user-" + UUID.randomUUID();

            Optional<IdempotencyRecord> result = idempotencyKeyService.acquireKey("   ", userId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("completeKey")
    class CompleteKey {

        @Test
        @DisplayName("should mark key as completed with response")
        void shouldMarkKeyAsCompletedWithResponse() {
            String idempotencyKey = "idem-complete-" + UUID.randomUUID();
            String userId = "user-" + UUID.randomUUID();
            String response = "{\"fileId\":\"file-123\",\"status\":\"uploaded\"}";
            
            idempotencyKeyService.acquireKey(idempotencyKey, userId);

            idempotencyKeyService.completeKey(idempotencyKey, userId, response);

            Optional<IdempotencyRecord> acquiredRecord = idempotencyKeyService.acquireKey(idempotencyKey, userId);
            assertThat(acquiredRecord).isPresent();
            assertThat(acquiredRecord.get().status()).isEqualTo(KeyStatus.COMPLETED);
            assertThat(acquiredRecord.get().response()).isEqualTo(response);
        }
    }

    @Nested
    @DisplayName("failKey")
    class FailKey {

        @Test
        @DisplayName("should allow reacquisition after failure")
        void shouldAllowReacquisitionAfterFailure() {
            String idempotencyKey = "idem-fail-" + UUID.randomUUID();
            String userId = "user-" + UUID.randomUUID();
            
            Optional<IdempotencyRecord> firstAcquire = idempotencyKeyService.acquireKey(idempotencyKey, userId);
            assertThat(firstAcquire).isEmpty();

            idempotencyKeyService.failKey(idempotencyKey, userId);

            Optional<IdempotencyRecord> secondAcquire = idempotencyKeyService.acquireKey(idempotencyKey, userId);
            assertThat(secondAcquire).isEmpty(); // Can acquire again after failure
        }
    }

    @Nested
    @DisplayName("idempotency workflow")
    class IdempotencyWorkflow {

        @Test
        @DisplayName("should differentiate keys by user ID")
        void shouldDifferentiateKeysByUserId() {
            String idempotencyKey = "idem-shared-" + UUID.randomUUID();
            String user1 = "user-1-" + UUID.randomUUID();
            String user2 = "user-2-" + UUID.randomUUID();
            
            idempotencyKeyService.acquireKey(idempotencyKey, user1);
            idempotencyKeyService.completeKey(idempotencyKey, user1, "{\"user\":\"1\"}");

            Optional<IdempotencyRecord> result = idempotencyKeyService.acquireKey(idempotencyKey, user2);

            assertThat(result).isEmpty();
            
            idempotencyKeyService.completeKey(idempotencyKey, user2, "{\"user\":\"2\"}");
            
            Optional<IdempotencyRecord> user1Record = idempotencyKeyService.acquireKey(idempotencyKey, user1);
            Optional<IdempotencyRecord> user2Record = idempotencyKeyService.acquireKey(idempotencyKey, user2);
            
            assertThat(user1Record).isPresent();
            assertThat(user1Record.get().response()).isEqualTo("{\"user\":\"1\"}");
            
            assertThat(user2Record).isPresent();
            assertThat(user2Record.get().response()).isEqualTo("{\"user\":\"2\"}");
        }

        @Test
        @DisplayName("should complete full idempotency workflow")
        void shouldCompleteFullIdempotencyWorkflow() {
            String idempotencyKey = "idem-workflow-" + UUID.randomUUID();
            String userId = "user-" + UUID.randomUUID();
            String expectedResponse = "{\"fileId\":\"abc-123\",\"status\":\"success\"}";

            Optional<IdempotencyRecord> firstRequest = idempotencyKeyService.acquireKey(idempotencyKey, userId);
            assertThat(firstRequest).isEmpty();

            idempotencyKeyService.completeKey(idempotencyKey, userId, expectedResponse);

            Optional<IdempotencyRecord> retryRequest = idempotencyKeyService.acquireKey(idempotencyKey, userId);
            assertThat(retryRequest).isPresent();
            assertThat(retryRequest.get().status()).isEqualTo(KeyStatus.COMPLETED);
            assertThat(retryRequest.get().response()).isEqualTo(expectedResponse);
        }
    }
}
