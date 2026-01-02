package io.github.pauszek.fsampgateway.integration;

import io.github.pauszek.fsampgateway.infrastructure.idempotency.IdempotencyKeyService;
import io.github.pauszek.fsampgateway.infrastructure.idempotency.IdempotencyKeyService.IdempotencyRecord;
import io.github.pauszek.fsampgateway.infrastructure.idempotency.IdempotencyKeyService.KeyStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for IdempotencyKeyService with real LocalStack DynamoDB.
 * 
 * Tests use only public API methods: acquireKey, completeKey, failKey
 */
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
            // given
            String idempotencyKey = "idem-" + UUID.randomUUID();
            String userId = "user-" + UUID.randomUUID();

            // when
            Optional<IdempotencyRecord> result = idempotencyKeyService.acquireKey(idempotencyKey, userId);

            // then - empty means key was acquired (first request)
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return existing record for duplicate key after completion")
        void shouldReturnExistingRecordForDuplicateKey() {
            // given
            String idempotencyKey = "idem-duplicate-" + UUID.randomUUID();
            String userId = "user-" + UUID.randomUUID();
            
            // First request - acquire key
            Optional<IdempotencyRecord> firstResult = idempotencyKeyService.acquireKey(idempotencyKey, userId);
            assertThat(firstResult).isEmpty(); // First acquisition succeeds
            
            // Complete the first request
            idempotencyKeyService.completeKey(idempotencyKey, userId, "{\"fileId\":\"123\"}");

            // when - second request with same key
            Optional<IdempotencyRecord> result = idempotencyKeyService.acquireKey(idempotencyKey, userId);

            // then - should return existing completed record
            assertThat(result).isPresent();
            assertThat(result.get().idempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(result.get().status()).isEqualTo(KeyStatus.COMPLETED);
            assertThat(result.get().response()).isEqualTo("{\"fileId\":\"123\"}");
        }

        @Test
        @DisplayName("should handle null idempotency key gracefully")
        void shouldHandleNullIdempotencyKeyGracefully() {
            // given
            String userId = "user-" + UUID.randomUUID();

            // when
            Optional<IdempotencyRecord> result = idempotencyKeyService.acquireKey(null, userId);

            // then - null key should skip idempotency check
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should handle blank idempotency key gracefully")
        void shouldHandleBlankIdempotencyKeyGracefully() {
            // given
            String userId = "user-" + UUID.randomUUID();

            // when
            Optional<IdempotencyRecord> result = idempotencyKeyService.acquireKey("   ", userId);

            // then - blank key should skip idempotency check
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("completeKey")
    class CompleteKey {

        @Test
        @DisplayName("should mark key as completed with response")
        void shouldMarkKeyAsCompletedWithResponse() {
            // given
            String idempotencyKey = "idem-complete-" + UUID.randomUUID();
            String userId = "user-" + UUID.randomUUID();
            String response = "{\"fileId\":\"file-123\",\"status\":\"uploaded\"}";
            
            // First acquire the key
            idempotencyKeyService.acquireKey(idempotencyKey, userId);

            // when
            idempotencyKeyService.completeKey(idempotencyKey, userId, response);

            // then - verify completion by trying to acquire again
            Optional<IdempotencyRecord> record = idempotencyKeyService.acquireKey(idempotencyKey, userId);
            assertThat(record).isPresent();
            assertThat(record.get().status()).isEqualTo(KeyStatus.COMPLETED);
            assertThat(record.get().response()).isEqualTo(response);
        }
    }

    @Nested
    @DisplayName("failKey")
    class FailKey {

        @Test
        @DisplayName("should allow reacquisition after failure")
        void shouldAllowReacquisitionAfterFailure() {
            // given
            String idempotencyKey = "idem-fail-" + UUID.randomUUID();
            String userId = "user-" + UUID.randomUUID();
            
            // First acquire the key
            Optional<IdempotencyRecord> firstAcquire = idempotencyKeyService.acquireKey(idempotencyKey, userId);
            assertThat(firstAcquire).isEmpty();

            // when - mark as failed (which deletes the key)
            idempotencyKeyService.failKey(idempotencyKey, userId);

            // then - should be able to acquire again
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
            // given
            String idempotencyKey = "idem-shared-" + UUID.randomUUID();
            String user1 = "user-1-" + UUID.randomUUID();
            String user2 = "user-2-" + UUID.randomUUID();
            
            // User 1 acquires and completes key
            idempotencyKeyService.acquireKey(idempotencyKey, user1);
            idempotencyKeyService.completeKey(idempotencyKey, user1, "{\"user\":\"1\"}");

            // when - User 2 tries to acquire same key
            Optional<IdempotencyRecord> result = idempotencyKeyService.acquireKey(idempotencyKey, user2);

            // then - User 2 should get their own key (empty = new acquisition)
            assertThat(result).isEmpty();
            
            // Complete user2's request
            idempotencyKeyService.completeKey(idempotencyKey, user2, "{\"user\":\"2\"}");
            
            // Verify both users have separate records
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
            // given
            String idempotencyKey = "idem-workflow-" + UUID.randomUUID();
            String userId = "user-" + UUID.randomUUID();
            String expectedResponse = "{\"fileId\":\"abc-123\",\"status\":\"success\"}";

            // Step 1: First request acquires key
            Optional<IdempotencyRecord> firstRequest = idempotencyKeyService.acquireKey(idempotencyKey, userId);
            assertThat(firstRequest).isEmpty();

            // Step 2: Process completes and stores response
            idempotencyKeyService.completeKey(idempotencyKey, userId, expectedResponse);

            // Step 3: Retry/duplicate request gets cached response
            Optional<IdempotencyRecord> retryRequest = idempotencyKeyService.acquireKey(idempotencyKey, userId);
            assertThat(retryRequest).isPresent();
            assertThat(retryRequest.get().status()).isEqualTo(KeyStatus.COMPLETED);
            assertThat(retryRequest.get().response()).isEqualTo(expectedResponse);
        }
    }
}
