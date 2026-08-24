package io.github.pauszek.fsampgateway.integration;

import io.github.pauszek.fsampgateway.infrastructure.idempotency.IdempotencyConflictException;
import io.github.pauszek.fsampgateway.infrastructure.idempotency.IdempotencyKeyService;
import io.github.pauszek.fsampgateway.infrastructure.idempotency.InvalidIdempotencyKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IdempotencyKeyService Integration Tests")
class IdempotencyKeyServiceIntegrationTest extends BaseIntegrationTest {

    private static final String FINGERPRINT = "a".repeat(64);

    @Autowired
    private IdempotencyKeyService idempotencyKeyService;

    @Nested
    class AcquireKey {

        @Test
        void shouldAcquireAndCacheACompletedResponse() {
            String key = uniqueKey("complete");
            String user = uniqueUser();

            IdempotencyKeyService.Acquisition first =
                    idempotencyKeyService.acquireKey(key, user, FINGERPRINT);
            assertThat(first.hasCachedResponse()).isFalse();
            assertThat(first.operationId()).isNotBlank();

            idempotencyKeyService.completeKey(
                    key,
                    user,
                    first.ownerToken(),
                    "{\"statusCode\":201,\"headers\":{},\"body\":\"{}\"}"
            );
            IdempotencyKeyService.Acquisition retry =
                    idempotencyKeyService.acquireKey(key, user, FINGERPRINT);

            assertThat(retry.hasCachedResponse()).isTrue();
            assertThat(retry.cachedRecord().status())
                    .isEqualTo(IdempotencyKeyService.KeyStatus.COMPLETED);
            assertThat(retry.cachedRecord().requestFingerprint()).isEqualTo(FINGERPRINT);
            assertThat(retry.operationId()).isEqualTo(first.operationId());
        }

        @Test
        void shouldRejectTheSameKeyForDifferentContent() {
            String key = uniqueKey("mismatch");
            String user = uniqueUser();
            IdempotencyKeyService.Acquisition first =
                    idempotencyKeyService.acquireKey(key, user, FINGERPRINT);
            idempotencyKeyService.completeKey(key, user, first.ownerToken(), "response");

            assertThatThrownBy(() -> idempotencyKeyService.acquireKey(key, user, "b".repeat(64)))
                    .isInstanceOf(IdempotencyConflictException.class)
                    .hasMessageContaining("different request");
        }

        @Test
        void shouldRejectInvalidKeys() {
            String user = uniqueUser();
            assertThatThrownBy(() ->
                    idempotencyKeyService.acquireKey("contains whitespace", user, FINGERPRINT))
                    .isInstanceOf(InvalidIdempotencyKeyException.class);
        }
    }

    @Nested
    class FailureRecovery {

        @Test
        void shouldAllowReacquisitionAfterTheOwnerReleasesAFailedRequest() {
            String key = uniqueKey("fail");
            String user = uniqueUser();
            IdempotencyKeyService.Acquisition first =
                    idempotencyKeyService.acquireKey(key, user, FINGERPRINT);

            idempotencyKeyService.failKey(key, user, first.ownerToken());
            IdempotencyKeyService.Acquisition second =
                    idempotencyKeyService.acquireKey(key, user, FINGERPRINT);

            assertThat(second.hasCachedResponse()).isFalse();
            assertThat(second.ownerToken()).isNotEqualTo(first.ownerToken());
            assertThat(second.operationId()).isNotEqualTo(first.operationId());
        }

        @Test
        void shouldKeepOperationIdWhenAnExpiredLeaseIsTakenOver() {
            String key = uniqueKey("takeover");
            String user = uniqueUser();
            IdempotencyKeyService.Acquisition first =
                    idempotencyKeyService.acquireKey(key, user, FINGERPRINT);
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(TEST_IDEMPOTENCY_TABLE)
                    .key(Map.of(
                            "idempotencyKey", AttributeValue.fromS(key),
                            "userId", AttributeValue.fromS(user)
                    ))
                    .updateExpression("SET createdAt = :expired")
                    .expressionAttributeValues(Map.of(
                            ":expired",
                            AttributeValue.fromS(
                                    Instant.now().minus(10, ChronoUnit.MINUTES).toString()
                            )
                    ))
                    .build());

            IdempotencyKeyService.Acquisition takeover =
                    idempotencyKeyService.acquireKey(key, user, FINGERPRINT);

            assertThat(takeover.ownerToken()).isNotEqualTo(first.ownerToken());
            assertThat(takeover.operationId()).isEqualTo(first.operationId());
        }
    }

    @Test
    void shouldIsolateTheSameKeyByAuthenticatedUser() {
        String key = uniqueKey("shared");
        String firstUser = uniqueUser();
        String secondUser = uniqueUser();

        IdempotencyKeyService.Acquisition first =
                idempotencyKeyService.acquireKey(key, firstUser, FINGERPRINT);
        IdempotencyKeyService.Acquisition second =
                idempotencyKeyService.acquireKey(key, secondUser, FINGERPRINT);
        idempotencyKeyService.completeKey(key, firstUser, first.ownerToken(), "first");
        idempotencyKeyService.completeKey(key, secondUser, second.ownerToken(), "second");

        assertThat(idempotencyKeyService.acquireKey(key, firstUser, FINGERPRINT)
                .cachedRecord().response()).isEqualTo("first");
        assertThat(idempotencyKeyService.acquireKey(key, secondUser, FINGERPRINT)
                .cachedRecord().response()).isEqualTo("second");
    }

    private static String uniqueKey(String purpose) {
        return "idem-" + purpose + "-" + UUID.randomUUID();
    }

    private static String uniqueUser() {
        return "user-" + UUID.randomUUID();
    }
}
