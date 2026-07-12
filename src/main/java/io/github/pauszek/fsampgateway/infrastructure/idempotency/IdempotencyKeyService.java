package io.github.pauszek.fsampgateway.infrastructure.idempotency;

import lombok.extern.slf4j.Slf4j;
import io.github.pauszek.fsampgateway.infrastructure.security.Sha256Digest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
public class IdempotencyKeyService {

    private static final String PK = "idempotencyKey";
    private static final String SK = "userId";
    private static final String STATUS_ATTR = "status";
    private static final String RESPONSE_ATTR = "response";
    private static final String FINGERPRINT_ATTR = "requestFingerprint";
    private static final String OWNER_TOKEN_ATTR = "ownerToken";
    private static final String CREATED_AT_ATTR = "createdAt";
    private static final String TTL_ATTR = "ttl";
    private static final int TTL_HOURS = 24;
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(5);
    private static final Pattern VALID_KEY = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public IdempotencyKeyService(
            DynamoDbClient dynamoDbClient,
            @Value("${aws.dynamodb.idempotency-table-name}") String tableName
    ) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public enum KeyStatus {
        IN_PROGRESS,
        COMPLETED
    }

    public record IdempotencyRecord(
            String idempotencyKey,
            String userId,
            KeyStatus status,
            String requestFingerprint,
            String ownerToken,
            String response,
            Instant createdAt
    ) {}

    public record Acquisition(String ownerToken, IdempotencyRecord cachedRecord) {
        static Acquisition acquired(String ownerToken) {
            return new Acquisition(ownerToken, null);
        }

        static Acquisition cached(IdempotencyRecord record) {
            return new Acquisition(null, record);
        }

        public boolean hasCachedResponse() {
            return cachedRecord != null;
        }
    }

    public Acquisition acquireKey(String idempotencyKey, String userId, String requestFingerprint) {
        validateKey(idempotencyKey);
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("Authenticated user is required for idempotency");
        }
        if (requestFingerprint == null || requestFingerprint.isBlank()) {
            throw new IllegalArgumentException("Request fingerprint is required");
        }

        String ownerToken = UUID.randomUUID().toString();
        Instant now = Instant.now();
        try {
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(newInProgressItem(idempotencyKey, userId, requestFingerprint, ownerToken, now))
                    .conditionExpression("attribute_not_exists(#pk) AND attribute_not_exists(#sk)")
                    .expressionAttributeNames(Map.of("#pk", PK, "#sk", SK))
                    .build());
            log.info("Acquired idempotency key: keyHash={}", keyHash(idempotencyKey));
            return Acquisition.acquired(ownerToken);
        } catch (ConditionalCheckFailedException e) {
            return resolveExisting(idempotencyKey, userId, requestFingerprint, ownerToken, now);
        }
    }

    public void completeKey(
            String idempotencyKey,
            String userId,
            String ownerToken,
            String response
    ) {
        if (response == null) {
            throw new IllegalArgumentException("Cached response cannot be null");
        }
        Instant now = Instant.now();
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key(idempotencyKey, userId))
                .updateExpression("SET #status = :completed, #response = :response, #ttl = :ttl")
                .conditionExpression("#status = :inProgress AND #owner = :owner")
                .expressionAttributeNames(Map.of(
                        "#status", STATUS_ATTR,
                        "#response", RESPONSE_ATTR,
                        "#ttl", TTL_ATTR,
                        "#owner", OWNER_TOKEN_ATTR
                ))
                .expressionAttributeValues(Map.of(
                        ":completed", s(KeyStatus.COMPLETED.name()),
                        ":inProgress", s(KeyStatus.IN_PROGRESS.name()),
                        ":response", s(response),
                        ":ttl", n(now.plus(TTL_HOURS, ChronoUnit.HOURS).getEpochSecond()),
                        ":owner", s(ownerToken)
                ))
                .build());
    }

    public void failKey(String idempotencyKey, String userId, String ownerToken) {
        try {
            dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(key(idempotencyKey, userId))
                    .conditionExpression("#status = :inProgress AND #owner = :owner")
                    .expressionAttributeNames(Map.of(
                            "#status", STATUS_ATTR,
                            "#owner", OWNER_TOKEN_ATTR
                    ))
                    .expressionAttributeValues(Map.of(
                            ":inProgress", s(KeyStatus.IN_PROGRESS.name()),
                            ":owner", s(ownerToken)
                    ))
                    .build());
        } catch (ConditionalCheckFailedException e) {
            log.warn("Did not release idempotency key owned by another request: keyHash={}",
                    keyHash(idempotencyKey));
        }
    }

    private Acquisition resolveExisting(
            String idempotencyKey,
            String userId,
            String requestFingerprint,
            String newOwnerToken,
            Instant now
    ) {
        IdempotencyRecord existing = getKey(idempotencyKey, userId)
                .orElseThrow(() -> new IdempotencyConflictException(
                        "Idempotency key changed concurrently; retry the request"));

        if (!requestFingerprint.equals(existing.requestFingerprint())) {
            throw new IdempotencyConflictException(
                    "Idempotency key was already used for a different request");
        }
        if (existing.status() == KeyStatus.COMPLETED) {
            if (existing.response() == null) {
                throw new IdempotencyConflictException(
                        "Completed idempotency record has no cached response");
            }
            return Acquisition.cached(existing);
        }
        if (existing.createdAt().plus(PROCESSING_LEASE).isAfter(now)) {
            throw new IdempotencyConflictException(
                    "Request with this idempotency key is already being processed");
        }

        try {
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key(idempotencyKey, userId))
                    .updateExpression("SET #owner = :newOwner, #created = :now, #fingerprint = :fingerprint, #ttl = :ttl REMOVE #response")
                    .conditionExpression("#status = :inProgress AND #created = :previousCreated AND #fingerprint = :fingerprint")
                    .expressionAttributeNames(Map.of(
                            "#owner", OWNER_TOKEN_ATTR,
                            "#created", CREATED_AT_ATTR,
                            "#fingerprint", FINGERPRINT_ATTR,
                            "#ttl", TTL_ATTR,
                            "#response", RESPONSE_ATTR,
                            "#status", STATUS_ATTR
                    ))
                    .expressionAttributeValues(Map.of(
                            ":newOwner", s(newOwnerToken),
                            ":now", s(now.toString()),
                            ":fingerprint", s(requestFingerprint),
                            ":ttl", n(now.plus(TTL_HOURS, ChronoUnit.HOURS).getEpochSecond()),
                            ":inProgress", s(KeyStatus.IN_PROGRESS.name()),
                            ":previousCreated", s(existing.createdAt().toString())
                    ))
                    .build());
            return Acquisition.acquired(newOwnerToken);
        } catch (ConditionalCheckFailedException e) {
            throw new IdempotencyConflictException(
                    "Another request acquired the expired idempotency lease");
        }
    }

    private Optional<IdempotencyRecord> getKey(String idempotencyKey, String userId) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key(idempotencyKey, userId))
                .consistentRead(true)
                .build());
        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }
        Map<String, AttributeValue> item = response.item();
        return Optional.of(new IdempotencyRecord(
                item.get(PK).s(),
                item.get(SK).s(),
                KeyStatus.valueOf(item.get(STATUS_ATTR).s()),
                item.containsKey(FINGERPRINT_ATTR) ? item.get(FINGERPRINT_ATTR).s() : null,
                item.containsKey(OWNER_TOKEN_ATTR) ? item.get(OWNER_TOKEN_ATTR).s() : null,
                item.containsKey(RESPONSE_ATTR) ? item.get(RESPONSE_ATTR).s() : null,
                Instant.parse(item.get(CREATED_AT_ATTR).s())
        ));
    }

    private static Map<String, AttributeValue> newInProgressItem(
            String idempotencyKey,
            String userId,
            String fingerprint,
            String ownerToken,
            Instant now
    ) {
        return Map.of(
                PK, s(idempotencyKey),
                SK, s(userId),
                STATUS_ATTR, s(KeyStatus.IN_PROGRESS.name()),
                FINGERPRINT_ATTR, s(fingerprint),
                OWNER_TOKEN_ATTR, s(ownerToken),
                CREATED_AT_ATTR, s(now.toString()),
                TTL_ATTR, n(now.plus(TTL_HOURS, ChronoUnit.HOURS).getEpochSecond())
        );
    }

    private static Map<String, AttributeValue> key(String idempotencyKey, String userId) {
        return Map.of(PK, s(idempotencyKey), SK, s(userId));
    }

    private static void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || !VALID_KEY.matcher(idempotencyKey).matches()) {
            throw new InvalidIdempotencyKeyException(
                    "X-Idempotency-Key must contain 1-128 letters, digits, '.', '_', ':' or '-'");
        }
    }

    private static String keyHash(String key) {
        byte[] digest = Sha256Digest.digest(key.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest, 0, 6);
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue n(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }
}
