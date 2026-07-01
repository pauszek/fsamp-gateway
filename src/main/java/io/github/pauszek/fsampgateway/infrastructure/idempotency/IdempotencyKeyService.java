package io.github.pauszek.fsampgateway.infrastructure.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class IdempotencyKeyService {

    private final String tableName;
    private static final String PK = "idempotencyKey";
    private static final String SK = "userId";
    private static final String RESPONSE_ATTR = "response";
    private static final String STATUS_ATTR = "status";
    private static final String TTL_ATTR = "ttl";
    private static final String CREATED_AT_ATTR = "createdAt";
    private static final long TTL_HOURS = 24;

    private final DynamoDbClient dynamoDbClient;

    public IdempotencyKeyService(
            DynamoDbClient dynamoDbClient,
            @org.springframework.beans.factory.annotation.Value("${aws.dynamodb.idempotency-table-name:${aws.dynamodb.table-name}-idempotency-keys}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public enum KeyStatus {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    public record IdempotencyRecord(
            String idempotencyKey,
            String userId,
            KeyStatus status,
            String response,
            Instant createdAt
    ) {}

    public Optional<IdempotencyRecord> acquireKey(String idempotencyKey, String userId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.debug("No idempotency key provided, skipping idempotency check");
            return Optional.empty();
        }

        log.info("Attempting to acquire idempotency key: key={}, userId={}", idempotencyKey, userId);

        Optional<IdempotencyRecord> existing = getKey(idempotencyKey, userId);
        if (existing.isPresent()) {
            IdempotencyRecord existingRecord = existing.get();
            if (existingRecord.status() == KeyStatus.COMPLETED) {
                log.info("Idempotency key already completed: key={}", idempotencyKey);
                return existing;
            }
            if (existingRecord.status() == KeyStatus.IN_PROGRESS) {
                if (existingRecord.createdAt().plus(5, ChronoUnit.MINUTES).isBefore(Instant.now())) {
                    log.warn("Found stale IN_PROGRESS key, allowing retry: key={}", idempotencyKey);
                    deleteKey(idempotencyKey, userId);
                } else {
                    log.warn("Request already in progress: key={}", idempotencyKey);
                    throw new IdempotencyConflictException(
                            "Request with this idempotency key is already being processed");
                }
            }
        }

        try {
            Instant now = Instant.now();
            long ttlEpochSeconds = now.plus(TTL_HOURS, ChronoUnit.HOURS).getEpochSecond();

            PutItemRequest putRequest = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                            PK, AttributeValue.builder().s(idempotencyKey).build(),
                            SK, AttributeValue.builder().s(userId).build(),
                            STATUS_ATTR, AttributeValue.builder().s(KeyStatus.IN_PROGRESS.name()).build(),
                            CREATED_AT_ATTR, AttributeValue.builder().s(now.toString()).build(),
                            TTL_ATTR, AttributeValue.builder().n(String.valueOf(ttlEpochSeconds)).build()
                    ))
                    .conditionExpression("attribute_not_exists(#pk)")
                    .expressionAttributeNames(Map.of("#pk", PK))
                    .build();

            dynamoDbClient.putItem(putRequest);
            log.info("Acquired idempotency key: key={}", idempotencyKey);
            return Optional.empty(); // Key acquired, proceed with processing

        } catch (ConditionalCheckFailedException e) {
            log.warn("Failed to acquire idempotency key (race condition): key={}", idempotencyKey);
            return getKey(idempotencyKey, userId);
        }
    }

    public void completeKey(String idempotencyKey, String userId, String response) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        log.info("Completing idempotency key: key={}", idempotencyKey);

        Instant now = Instant.now();
        long ttlEpochSeconds = now.plus(TTL_HOURS, ChronoUnit.HOURS).getEpochSecond();

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        PK, AttributeValue.builder().s(idempotencyKey).build(),
                        SK, AttributeValue.builder().s(userId).build()
                ))
                .updateExpression("SET #status = :status, #response = :response, #ttl = :ttl")
                .expressionAttributeNames(Map.of(
                        "#status", STATUS_ATTR,
                        "#response", RESPONSE_ATTR,
                        "#ttl", TTL_ATTR
                ))
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.builder().s(KeyStatus.COMPLETED.name()).build(),
                        ":response", AttributeValue.builder().s(response).build(),
                        ":ttl", AttributeValue.builder().n(String.valueOf(ttlEpochSeconds)).build()
                ))
                .build();

        dynamoDbClient.updateItem(updateRequest);
        log.info("Completed idempotency key: key={}", idempotencyKey);
    }

    public void failKey(String idempotencyKey, String userId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        log.info("Marking idempotency key as failed: key={}", idempotencyKey);
        deleteKey(idempotencyKey, userId);
    }

    private Optional<IdempotencyRecord> getKey(String idempotencyKey, String userId) {
        GetItemRequest getRequest = GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        PK, AttributeValue.builder().s(idempotencyKey).build(),
                        SK, AttributeValue.builder().s(userId).build()
                ))
                .build();

        GetItemResponse response = dynamoDbClient.getItem(getRequest);
        
        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }

        Map<String, AttributeValue> item = response.item();
        return Optional.of(new IdempotencyRecord(
                item.get(PK).s(),
                item.get(SK).s(),
                KeyStatus.valueOf(item.get(STATUS_ATTR).s()),
                item.containsKey(RESPONSE_ATTR) ? item.get(RESPONSE_ATTR).s() : null,
                Instant.parse(item.get(CREATED_AT_ATTR).s())
        ));
    }

    private void deleteKey(String idempotencyKey, String userId) {
        DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        PK, AttributeValue.builder().s(idempotencyKey).build(),
                        SK, AttributeValue.builder().s(userId).build()
                ))
                .build();

        dynamoDbClient.deleteItem(deleteRequest);
    }
}
