package io.github.pauszek.fsampgateway.infrastructure.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * Idempotency Key Service.
 * 
 * Implements the Idempotency Key pattern for safe API retries.
 * 
 * Pattern benefits:
 * - Prevents duplicate file uploads during network retries
 * - Ensures exactly-once semantics for critical operations
 * - Client can safely retry without causing duplicate processing
 * 
 * Implementation:
 * - Uses DynamoDB with TTL for key storage
 * - Keys expire after 24 hours (configurable)
 * - Conditional writes ensure atomicity
 * 
 * Usage:
 * - Client sends X-Idempotency-Key header
 * - Server checks if key was already processed
 * - If yes: return cached response
 * - If no: process request, store result, return response
 */
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

    /**
     * Status of idempotency key processing.
     */
    public enum KeyStatus {
        /** Request is being processed */
        IN_PROGRESS,
        /** Request completed successfully */
        COMPLETED,
        /** Request failed */
        FAILED
    }

    /**
     * Record representing stored idempotency data.
     */
    public record IdempotencyRecord(
            String idempotencyKey,
            String userId,
            KeyStatus status,
            String response,
            Instant createdAt
    ) {}

    /**
     * Acquire an idempotency key for processing.
     * 
     * Uses conditional write to ensure only one request processes a key.
     * 
     * @param idempotencyKey Client-provided idempotency key
     * @param userId User making the request
     * @return Empty if key acquired (first request), or existing record if duplicate
     */
    public Optional<IdempotencyRecord> acquireKey(String idempotencyKey, String userId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.debug("No idempotency key provided, skipping idempotency check");
            return Optional.empty();
        }

        log.info("Attempting to acquire idempotency key: key={}, userId={}", idempotencyKey, userId);

        // First, try to get existing record
        Optional<IdempotencyRecord> existing = getKey(idempotencyKey, userId);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.status() == KeyStatus.COMPLETED) {
                log.info("Idempotency key already completed: key={}", idempotencyKey);
                return existing;
            }
            if (record.status() == KeyStatus.IN_PROGRESS) {
                // Check if it's stale (older than 5 minutes - likely a failed request)
                if (record.createdAt().plus(5, ChronoUnit.MINUTES).isBefore(Instant.now())) {
                    log.warn("Found stale IN_PROGRESS key, allowing retry: key={}", idempotencyKey);
                    // Delete stale record and proceed
                    deleteKey(idempotencyKey, userId);
                } else {
                    log.warn("Request already in progress: key={}", idempotencyKey);
                    throw new IdempotencyConflictException(
                            "Request with this idempotency key is already being processed");
                }
            }
        }

        // Try to create new IN_PROGRESS record with conditional write
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
            // Race condition - another request got the key
            log.warn("Failed to acquire idempotency key (race condition): key={}", idempotencyKey);
            return getKey(idempotencyKey, userId);
        }
    }

    /**
     * Complete an idempotency key with the response.
     * 
     * @param idempotencyKey The key to complete
     * @param userId User who made the request
     * @param response Serialized response to cache
     */
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

    /**
     * Mark an idempotency key as failed.
     * 
     * @param idempotencyKey The key to mark as failed
     * @param userId User who made the request
     */
    public void failKey(String idempotencyKey, String userId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        log.info("Marking idempotency key as failed: key={}", idempotencyKey);
        deleteKey(idempotencyKey, userId);
    }

    /**
     * Get an existing idempotency record.
     */
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

    /**
     * Delete an idempotency key (for cleanup or retry after failure).
     */
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
