package io.github.pauszek.fsampgateway.infrastructure.idempotency;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyKeyService")
class IdempotencyKeyServiceTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private IdempotencyKeyService service;

    @Captor
    private ArgumentCaptor<PutItemRequest> putRequestCaptor;

    @Captor
    private ArgumentCaptor<UpdateItemRequest> updateRequestCaptor;

    @Captor
    private ArgumentCaptor<DeleteItemRequest> deleteRequestCaptor;

    private static final String IDEM_KEY = "idem-key-123";
    private static final String USER_ID = "user-123";

    @Nested
    @DisplayName("acquireKey")
    class AcquireKey {

        @Test
        @DisplayName("should acquire new key and return empty Optional")
        void shouldAcquireNewKeyAndReturnEmpty() {
            // given
            mockNoExistingKey();
            given(dynamoDbClient.putItem(any(PutItemRequest.class)))
                    .willReturn(PutItemResponse.builder().build());

            // when
            Optional<IdempotencyKeyService.IdempotencyRecord> result = 
                    service.acquireKey(IDEM_KEY, USER_ID);

            // then
            assertThat(result).isEmpty();
            then(dynamoDbClient).should().putItem(putRequestCaptor.capture());
            assertThat(putRequestCaptor.getValue().item().get("idempotencyKey").s())
                    .isEqualTo(IDEM_KEY);
            assertThat(putRequestCaptor.getValue().item().get("status").s())
                    .isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("should return existing record for completed key")
        void shouldReturnExistingRecordForCompletedKey() {
            // given
            mockExistingCompletedKey();

            // when
            Optional<IdempotencyKeyService.IdempotencyRecord> result = 
                    service.acquireKey(IDEM_KEY, USER_ID);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().status())
                    .isEqualTo(IdempotencyKeyService.KeyStatus.COMPLETED);
            assertThat(result.get().response()).isEqualTo("{\"fileId\":\"123\"}");
            then(dynamoDbClient).should(never()).putItem(any(PutItemRequest.class));
        }

        @Test
        @DisplayName("should throw IdempotencyConflictException for IN_PROGRESS key")
        void shouldThrowIdempotencyConflictExceptionForInProgressKey() {
            // given
            mockExistingInProgressKey(Instant.now()); // recent, not stale

            // when/then
            assertThatThrownBy(() -> service.acquireKey(IDEM_KEY, USER_ID))
                    .isInstanceOf(IdempotencyConflictException.class)
                    .hasMessageContaining("already being processed");
        }

        @Test
        @DisplayName("should allow retry for stale IN_PROGRESS keys (>5 min)")
        void shouldAllowRetryForStaleInProgressKeys() {
            // given - key created 10 minutes ago
            Instant staleTime = Instant.now().minus(10, ChronoUnit.MINUTES);
            mockExistingInProgressKey(staleTime);
            given(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
                    .willReturn(DeleteItemResponse.builder().build());
            given(dynamoDbClient.putItem(any(PutItemRequest.class)))
                    .willReturn(PutItemResponse.builder().build());

            // when
            Optional<IdempotencyKeyService.IdempotencyRecord> result = 
                    service.acquireKey(IDEM_KEY, USER_ID);

            // then
            assertThat(result).isEmpty(); // key acquired
            then(dynamoDbClient).should().deleteItem(any(DeleteItemRequest.class));
            then(dynamoDbClient).should().putItem(any(PutItemRequest.class));
        }

        @Test
        @DisplayName("should handle race condition (ConditionalCheckFailedException)")
        void shouldHandleRaceCondition() {
            // given - no existing key on first check, but conditional write fails
            // First call returns empty, second call after race condition returns completed
            given(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .willReturn(GetItemResponse.builder().item(Map.of()).build())
                    .willReturn(createCompletedItemResponse());
            given(dynamoDbClient.putItem(any(PutItemRequest.class)))
                    .willThrow(ConditionalCheckFailedException.builder()
                            .message("Condition check failed").build());

            // when
            Optional<IdempotencyKeyService.IdempotencyRecord> result = 
                    service.acquireKey(IDEM_KEY, USER_ID);

            // then
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("should return empty for null idempotency key")
        void shouldReturnEmptyForNullKey() {
            // when
            Optional<IdempotencyKeyService.IdempotencyRecord> result = 
                    service.acquireKey(null, USER_ID);

            // then
            assertThat(result).isEmpty();
            then(dynamoDbClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should return empty for blank idempotency key")
        void shouldReturnEmptyForBlankKey() {
            // when
            Optional<IdempotencyKeyService.IdempotencyRecord> result = 
                    service.acquireKey("   ", USER_ID);

            // then
            assertThat(result).isEmpty();
            then(dynamoDbClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("completeKey")
    class CompleteKey {

        @Test
        @DisplayName("should complete key with serialized response")
        void shouldCompleteKeyWithSerializedResponse() {
            // given
            String response = "{\"fileId\":\"123\",\"status\":\"uploaded\"}";
            given(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                    .willReturn(UpdateItemResponse.builder().build());

            // when
            service.completeKey(IDEM_KEY, USER_ID, response);

            // then
            then(dynamoDbClient).should().updateItem(updateRequestCaptor.capture());
            UpdateItemRequest request = updateRequestCaptor.getValue();
            assertThat(request.expressionAttributeValues())
                    .containsEntry(":status", AttributeValue.fromS("COMPLETED"))
                    .containsEntry(":response", AttributeValue.fromS(response));
        }

        @Test
        @DisplayName("should do nothing for null key")
        void shouldDoNothingForNullKey() {
            // when
            service.completeKey(null, USER_ID, "response");

            // then
            then(dynamoDbClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should do nothing for blank key")
        void shouldDoNothingForBlankKey() {
            // when
            service.completeKey("  ", USER_ID, "response");

            // then
            then(dynamoDbClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("failKey")
    class FailKey {

        @Test
        @DisplayName("should delete key on failure")
        void shouldDeleteKeyOnFailure() {
            // given
            given(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
                    .willReturn(DeleteItemResponse.builder().build());

            // when
            service.failKey(IDEM_KEY, USER_ID);

            // then
            then(dynamoDbClient).should().deleteItem(deleteRequestCaptor.capture());
            assertThat(deleteRequestCaptor.getValue().key().get("idempotencyKey").s())
                    .isEqualTo(IDEM_KEY);
        }

        @Test
        @DisplayName("should do nothing for null key")
        void shouldDoNothingForNullKey() {
            // when
            service.failKey(null, USER_ID);

            // then
            then(dynamoDbClient).shouldHaveNoInteractions();
        }
    }

    // Helper methods

    private void mockNoExistingKey() {
        given(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .willReturn(GetItemResponse.builder().item(Map.of()).build());
    }

    private void mockExistingCompletedKey() {
        given(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .willReturn(createCompletedItemResponse());
    }

    private GetItemResponse createCompletedItemResponse() {
        return GetItemResponse.builder()
                .item(Map.of(
                        "idempotencyKey", AttributeValue.builder().s(IDEM_KEY).build(),
                        "userId", AttributeValue.builder().s(USER_ID).build(),
                        "status", AttributeValue.builder().s("COMPLETED").build(),
                        "response", AttributeValue.builder().s("{\"fileId\":\"123\"}").build(),
                        "createdAt", AttributeValue.builder().s(Instant.now().toString()).build()
                ))
                .build();
    }

    private void mockExistingInProgressKey(Instant createdAt) {
        given(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .willReturn(GetItemResponse.builder()
                        .item(Map.of(
                                "idempotencyKey", AttributeValue.builder().s(IDEM_KEY).build(),
                                "userId", AttributeValue.builder().s(USER_ID).build(),
                                "status", AttributeValue.builder().s("IN_PROGRESS").build(),
                                "createdAt", AttributeValue.builder().s(createdAt.toString()).build()
                        ))
                        .build());
    }
}
