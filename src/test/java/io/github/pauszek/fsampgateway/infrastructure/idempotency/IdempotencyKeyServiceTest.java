package io.github.pauszek.fsampgateway.infrastructure.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyKeyService")
class IdempotencyKeyServiceTest {

    private static final String IDEM_KEY = "idem-key-123";
    private static final String USER_ID = "user-123";
    private static final String FINGERPRINT = "a".repeat(64);
    private static final String TABLE = "test-idempotency";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Captor
    private ArgumentCaptor<PutItemRequest> putRequestCaptor;

    @Captor
    private ArgumentCaptor<GetItemRequest> getRequestCaptor;

    @Captor
    private ArgumentCaptor<UpdateItemRequest> updateRequestCaptor;

    @Captor
    private ArgumentCaptor<DeleteItemRequest> deleteRequestCaptor;

    private IdempotencyKeyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyKeyService(dynamoDbClient, TABLE);
    }

    @Nested
    @DisplayName("acquireKey")
    class AcquireKey {

        @Test
        void shouldAcquireNewKeyWithFingerprintAndOwnerToken() {
            given(dynamoDbClient.putItem(any(PutItemRequest.class)))
                    .willReturn(PutItemResponse.builder().build());

            IdempotencyKeyService.Acquisition result =
                    service.acquireKey(IDEM_KEY, USER_ID, FINGERPRINT);

            assertThat(result.hasCachedResponse()).isFalse();
            assertThat(result.ownerToken()).isNotBlank();
            then(dynamoDbClient).should().putItem(putRequestCaptor.capture());
            PutItemRequest request = putRequestCaptor.getValue();
            assertThat(request.conditionExpression())
                    .isEqualTo("attribute_not_exists(#pk) AND attribute_not_exists(#sk)");
            assertThat(request.item())
                    .containsEntry("idempotencyKey", AttributeValue.fromS(IDEM_KEY))
                    .containsEntry("userId", AttributeValue.fromS(USER_ID))
                    .containsEntry("status", AttributeValue.fromS("IN_PROGRESS"))
                    .containsEntry("requestFingerprint", AttributeValue.fromS(FINGERPRINT));
        }

        @Test
        void shouldReturnCompletedResponseAfterStronglyConsistentRead() {
            rejectInitialPut();
            given(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .willReturn(completedItem(FINGERPRINT));

            IdempotencyKeyService.Acquisition result =
                    service.acquireKey(IDEM_KEY, USER_ID, FINGERPRINT);

            assertThat(result.hasCachedResponse()).isTrue();
            assertThat(result.cachedRecord().response()).isEqualTo("{\"fileId\":\"123\"}");
            then(dynamoDbClient).should().getItem(getRequestCaptor.capture());
            assertThat(getRequestCaptor.getValue().consistentRead()).isTrue();
        }

        @Test
        void shouldRejectReuseForDifferentRequestFingerprint() {
            rejectInitialPut();
            given(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .willReturn(completedItem("b".repeat(64)));

            assertThatThrownBy(() -> service.acquireKey(IDEM_KEY, USER_ID, FINGERPRINT))
                    .isInstanceOf(IdempotencyConflictException.class)
                    .hasMessageContaining("different request");
        }

        @Test
        void shouldRejectRecentInProgressRequest() {
            rejectInitialPut();
            given(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .willReturn(inProgressItem(Instant.now(), FINGERPRINT));

            assertThatThrownBy(() -> service.acquireKey(IDEM_KEY, USER_ID, FINGERPRINT))
                    .isInstanceOf(IdempotencyConflictException.class)
                    .hasMessageContaining("already being processed");
        }

        @Test
        void shouldAtomicallyStealExpiredLeaseWithoutDeletePutRace() {
            rejectInitialPut();
            given(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .willReturn(inProgressItem(Instant.now().minus(10, ChronoUnit.MINUTES), FINGERPRINT));
            given(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                    .willReturn(UpdateItemResponse.builder().build());

            IdempotencyKeyService.Acquisition result =
                    service.acquireKey(IDEM_KEY, USER_ID, FINGERPRINT);

            assertThat(result.ownerToken()).isNotBlank();
            then(dynamoDbClient).should().updateItem(updateRequestCaptor.capture());
            assertThat(updateRequestCaptor.getValue().conditionExpression())
                    .contains("#created = :previousCreated")
                    .contains("#fingerprint = :fingerprint");
            then(dynamoDbClient).should(never()).deleteItem(any(DeleteItemRequest.class));
        }

        @Test
        void shouldRejectMissingOrMalformedKeysBeforeCallingDynamoDb() {
            assertThatThrownBy(() -> service.acquireKey(null, USER_ID, FINGERPRINT))
                    .isInstanceOf(InvalidIdempotencyKeyException.class);
            assertThatThrownBy(() -> service.acquireKey("contains whitespace", USER_ID, FINGERPRINT))
                    .isInstanceOf(InvalidIdempotencyKeyException.class);
            String oversizedKey = "x".repeat(129);
            assertThatThrownBy(() -> service.acquireKey(oversizedKey, USER_ID, FINGERPRINT))
                    .isInstanceOf(InvalidIdempotencyKeyException.class);
            then(dynamoDbClient).shouldHaveNoInteractions();
        }

        private void rejectInitialPut() {
            given(dynamoDbClient.putItem(any(PutItemRequest.class)))
                    .willThrow(ConditionalCheckFailedException.builder().message("exists").build());
        }
    }

    @Test
    void shouldCompleteOnlyTheLeaseOwnedByThisRequest() {
        given(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .willReturn(UpdateItemResponse.builder().build());

        service.completeKey(IDEM_KEY, USER_ID, "owner-token", "{\"status\":201}");

        then(dynamoDbClient).should().updateItem(updateRequestCaptor.capture());
        UpdateItemRequest request = updateRequestCaptor.getValue();
        assertThat(request.conditionExpression()).isEqualTo("#status = :inProgress AND #owner = :owner");
        assertThat(request.expressionAttributeValues())
                .containsEntry(":completed", AttributeValue.fromS("COMPLETED"))
                .containsEntry(":owner", AttributeValue.fromS("owner-token"))
                .containsEntry(":response", AttributeValue.fromS("{\"status\":201}"));
    }

    @Test
    void shouldReleaseOnlyTheLeaseOwnedByThisRequest() {
        service.failKey(IDEM_KEY, USER_ID, "owner-token");

        then(dynamoDbClient).should().deleteItem(deleteRequestCaptor.capture());
        DeleteItemRequest request = deleteRequestCaptor.getValue();
        assertThat(request.conditionExpression()).isEqualTo("#status = :inProgress AND #owner = :owner");
        assertThat(request.expressionAttributeValues())
                .containsEntry(":owner", AttributeValue.fromS("owner-token"));
    }

    private static GetItemResponse completedItem(String fingerprint) {
        return GetItemResponse.builder().item(Map.of(
                "idempotencyKey", AttributeValue.fromS(IDEM_KEY),
                "userId", AttributeValue.fromS(USER_ID),
                "status", AttributeValue.fromS("COMPLETED"),
                "requestFingerprint", AttributeValue.fromS(fingerprint),
                "ownerToken", AttributeValue.fromS("owner-token"),
                "response", AttributeValue.fromS("{\"fileId\":\"123\"}"),
                "createdAt", AttributeValue.fromS(Instant.now().toString())
        )).build();
    }

    private static GetItemResponse inProgressItem(Instant createdAt, String fingerprint) {
        return GetItemResponse.builder().item(Map.of(
                "idempotencyKey", AttributeValue.fromS(IDEM_KEY),
                "userId", AttributeValue.fromS(USER_ID),
                "status", AttributeValue.fromS("IN_PROGRESS"),
                "requestFingerprint", AttributeValue.fromS(fingerprint),
                "ownerToken", AttributeValue.fromS("old-owner"),
                "createdAt", AttributeValue.fromS(createdAt.toString())
        )).build();
    }
}
