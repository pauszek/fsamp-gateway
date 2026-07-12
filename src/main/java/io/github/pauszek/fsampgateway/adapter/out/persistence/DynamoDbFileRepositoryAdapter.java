package io.github.pauszek.fsampgateway.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pauszek.fsampgateway.adapter.out.messaging.EventContractValidator;
import io.github.pauszek.fsampgateway.domain.event.DomainEvent;
import io.github.pauszek.fsampgateway.domain.event.FileUploadedEvent;
import io.github.pauszek.fsampgateway.domain.exception.EventPublishException;
import io.github.pauszek.fsampgateway.domain.model.AuditInfo;
import io.github.pauszek.fsampgateway.domain.model.Checksum;
import io.github.pauszek.fsampgateway.domain.model.CorrelationId;
import io.github.pauszek.fsampgateway.domain.model.EncryptionMetadata;
import io.github.pauszek.fsampgateway.domain.model.FileId;
import io.github.pauszek.fsampgateway.domain.model.FileName;
import io.github.pauszek.fsampgateway.domain.model.FileSize;
import io.github.pauszek.fsampgateway.domain.model.FileStatus;
import io.github.pauszek.fsampgateway.domain.model.MimeType;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;
import io.github.pauszek.fsampgateway.domain.model.StorageLocation;
import io.github.pauszek.fsampgateway.domain.port.out.FileRepositoryPort;
import io.github.pauszek.fsampgateway.infrastructure.security.Sha256Digest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
@Primary
@Profile("!test")
public class DynamoDbFileRepositoryAdapter implements FileRepositoryPort {

    static final String CURRENT_STATE_SK = "METADATA";
    static final String FILE_METADATA_ENTITY_TYPE = "FILE_METADATA";
    private static final String OUTBOX_ENTITY_TYPE = "OUTBOX_EVENT";
    private static final String OUTBOX_STATUS_PENDING = "PENDING";
    private static final int OUTBOX_SHARD_COUNT = 16;

    private static final String PK = "PK";
    private static final String SK = "SK";
    private static final String GSI1_PK = "GSI1PK";
    private static final String GSI1_SK = "GSI1SK";
    private static final String ATTR_ENTITY_TYPE = "entityType";
    private static final String ATTR_FILE_ID = "fileId";
    private static final String ATTR_CORRELATION_ID = "correlationId";
    private static final String ATTR_ORIGINAL_FILENAME = "originalFilename";
    private static final String LEGACY_ATTR_FILE_NAME = "fileName";
    private static final String ATTR_DESCRIPTION = "description";
    private static final String ATTR_TAGS = "tags";
    private static final String ATTR_MIME_TYPE = "mimeType";
    private static final String ATTR_FILE_SIZE = "fileSizeBytes";
    private static final String ATTR_CHECKSUM = "checksumSHA256";
    private static final String ATTR_CHECKSUM_ALGORITHM = "checksumAlgorithm";
    private static final String ATTR_STATUS = "status";
    private static final String ATTR_BUCKET_NAME = "bucketName";
    private static final String ATTR_OBJECT_KEY = "objectKey";
    private static final String ATTR_KMS_KEY_ID = "kmsKeyId";
    private static final String ATTR_ENCRYPTION_ALGORITHM = "encryptionAlgorithm";
    private static final String ATTR_IS_ENCRYPTED = "isEncrypted";
    private static final String ATTR_CREATED_BY = "createdBy";
    private static final String ATTR_CREATED_AT = "createdAt";
    private static final String ATTR_UPDATED_AT = "updatedAt";

    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;
    private final EventContractValidator eventContractValidator;
    private final String tableName;
    private final String outboxTableName;

    @Autowired
    public DynamoDbFileRepositoryAdapter(
            DynamoDbClient dynamoDbClient,
            ObjectMapper objectMapper,
            EventContractValidator eventContractValidator,
            @Value("${aws.dynamodb.table-name}") String tableName,
            @Value("${aws.dynamodb.outbox-table-name:}") String outboxTableName
    ) {
        this.dynamoDbClient = dynamoDbClient;
        this.objectMapper = objectMapper;
        this.eventContractValidator = eventContractValidator;
        this.tableName = tableName;
        this.outboxTableName = outboxTableName;
        LoggerFactory.getLogger(getClass()).info(
                "DynamoDB file repository initialized: table={}, outboxEnabled={}",
                tableName,
                supportsTransactionalOutbox()
        );
    }

    DynamoDbFileRepositoryAdapter(
            DynamoDbClient dynamoDbClient,
            ObjectMapper objectMapper,
            String tableName,
            String outboxTableName
    ) {
        this(dynamoDbClient, objectMapper, null, tableName, outboxTableName);
    }

    @Override
    @CircuitBreaker(name = "dynamoDb")
    @Retry(name = "dynamoDb")
    public SecureFile save(SecureFile file) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(toItem(file))
                .build());
        return file;
    }

    @Override
    public boolean supportsTransactionalOutbox() {
        return outboxTableName != null && !outboxTableName.isBlank();
    }

    @Override
    @CircuitBreaker(name = "dynamoDb")
    @Retry(name = "dynamoDb")
    public SecureFile saveWithOutbox(SecureFile file, DomainEvent event) {
        if (!supportsTransactionalOutbox()) {
            return save(file);
        }
        validateEvent(event);
        Map<String, AttributeValue> metadataItem = toItem(file);
        Map<String, AttributeValue> outboxItem = toOutboxItem(file, event);

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .clientRequestToken(eventId(event))
                .transactItems(
                        TransactWriteItem.builder()
                                .put(put -> put
                                        .tableName(tableName)
                                        .item(metadataItem)
                                        .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)"))
                                .build(),
                        TransactWriteItem.builder()
                                .put(put -> put
                                        .tableName(outboxTableName)
                                        .item(outboxItem)
                                        .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)"))
                                .build()
                )
                .build();

        dynamoDbClient.transactWriteItems(request);
        return file;
    }

    @Override
    @CircuitBreaker(name = "dynamoDb")
    @Retry(name = "dynamoDb")
    public Optional<SecureFile> findById(FileId fileId) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(currentStateKey(fileId))
                .consistentRead(true)
                .build());
        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fromItem(response.item()));
    }

    @Override
    @CircuitBreaker(name = "dynamoDb")
    @Retry(name = "dynamoDb")
    public void delete(FileId fileId) {
        dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(currentStateKey(fileId))
                .build());
    }

    @Override
    public boolean exists(FileId fileId) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(currentStateKey(fileId))
                .consistentRead(true)
                .projectionExpression(PK)
                .build());
        return response.hasItem() && !response.item().isEmpty();
    }

    private Map<String, AttributeValue> toItem(SecureFile file) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.putAll(currentStateKey(file.getId()));
        item.put(ATTR_ENTITY_TYPE, s(FILE_METADATA_ENTITY_TYPE));
        item.put(ATTR_FILE_ID, s(file.getId().toString()));
        item.put(ATTR_CORRELATION_ID, s(file.getCorrelationId().value()));
        item.put(ATTR_ORIGINAL_FILENAME, s(file.getFileName().value()));
        item.put(ATTR_MIME_TYPE, s(file.getMimeType().value()));
        item.put(ATTR_FILE_SIZE, n(file.getSize().bytes()));
        item.put(ATTR_STATUS, s(file.getStatus().name()));
        item.put(GSI1_PK, s("STATUS#" + file.getStatus().name()));
        item.put(GSI1_SK, s(file.getAuditInfo().updatedAt().toString()));

        putOptionalString(item, ATTR_DESCRIPTION, file.getDescription());
        if (!file.getTags().isEmpty()) {
            item.put(ATTR_TAGS, AttributeValue.builder().ss(file.getTags()).build());
        }
        if (file.getChecksum() != null) {
            item.put(ATTR_CHECKSUM, s(file.getChecksum().value()));
            item.put(ATTR_CHECKSUM_ALGORITHM, s(file.getChecksum().algorithm().name()));
        }
        if (file.getStorageLocation() != null) {
            item.put(ATTR_BUCKET_NAME, s(file.getStorageLocation().bucketName()));
            item.put(ATTR_OBJECT_KEY, s(file.getStorageLocation().objectKey()));
        }
        if (file.getEncryptionMetadata() != null) {
            item.put(ATTR_KMS_KEY_ID, s(file.getEncryptionMetadata().kmsKeyId()));
            item.put(ATTR_ENCRYPTION_ALGORITHM, s(file.getEncryptionMetadata().getAlgorithmName()));
            item.put(ATTR_IS_ENCRYPTED, bool(file.getEncryptionMetadata().encrypted()));
        }
        item.put(ATTR_CREATED_BY, s(file.getAuditInfo().createdBy()));
        item.put(ATTR_CREATED_AT, s(file.getAuditInfo().createdAt().toString()));
        item.put(ATTR_UPDATED_AT, s(file.getAuditInfo().updatedAt().toString()));
        return item;
    }

    private Map<String, AttributeValue> toOutboxItem(SecureFile file, DomainEvent event) {
        try {
            String eventId = eventId(event);
            String aggregateType = "FileUpload";
            String aggregateId = file.getId().toString();
            String shard = outboxShard(aggregateId);
            String partition = "OUTBOX#" + aggregateType + "#" + aggregateId;
            String createdAt = event.getOccurredAt().toString();

            Map<String, AttributeValue> item = new HashMap<>();
            item.put(PK, s(partition));
            item.put(SK, s("EVENT#" + eventId));
            item.put(ATTR_ENTITY_TYPE, s(OUTBOX_ENTITY_TYPE));
            item.put("outboxPartition", s(partition));
            item.put("outboxShard", s(shard));
            item.put("eventId", s(eventId));
            item.put("eventType", s(event.getEventType()));
            item.put("aggregateId", s(aggregateId));
            item.put("aggregateType", s(aggregateType));
            item.put("payload", s(objectMapper.writeValueAsString(event)));
            item.put(ATTR_STATUS, s(OUTBOX_STATUS_PENDING));
            item.put(ATTR_CREATED_AT, s(createdAt));
            item.put(ATTR_UPDATED_AT, s(createdAt));
            item.put("retryCount", n(0));
            item.put("messageGroupId", s(aggregateId));
            item.put(GSI1_PK, s("STATUS#" + OUTBOX_STATUS_PENDING + "#" + shard));
            item.put(GSI1_SK, s(createdAt));
            return item;
        } catch (JsonProcessingException e) {
            throw new EventPublishException("Failed to serialize event for outbox", e);
        }
    }

    private SecureFile fromItem(Map<String, AttributeValue> item) {
        String entityType = stringValue(item, ATTR_ENTITY_TYPE);
        if (entityType != null && !FILE_METADATA_ENTITY_TYPE.equals(entityType)) {
            throw new IllegalStateException("Unexpected DynamoDB entity type: " + entityType);
        }
        String originalFilename = firstString(item, ATTR_ORIGINAL_FILENAME, LEGACY_ATTR_FILE_NAME);
        SecureFile.Builder builder = SecureFile.builder()
                .id(FileId.of(readFileId(item)))
                .correlationId(CorrelationId.of(requiredString(item, ATTR_CORRELATION_ID)))
                .fileName(FileName.of(requireValue(originalFilename, ATTR_ORIGINAL_FILENAME)))
                .description(stringValue(item, ATTR_DESCRIPTION))
                .tags(stringSet(item, ATTR_TAGS))
                .mimeType(MimeType.of(requiredString(item, ATTR_MIME_TYPE)))
                .size(FileSize.of(Long.parseLong(requiredNumber(item, ATTR_FILE_SIZE))))
                .status(FileStatus.valueOf(requiredString(item, ATTR_STATUS)))
                .auditInfo(new AuditInfo(
                        requiredString(item, ATTR_CREATED_BY),
                        Instant.parse(requiredString(item, ATTR_CREATED_AT)),
                        Instant.parse(requiredString(item, ATTR_UPDATED_AT))
                ));

        if (stringValue(item, ATTR_CHECKSUM) != null) {
            String algorithm = Optional.ofNullable(stringValue(item, ATTR_CHECKSUM_ALGORITHM))
                    .orElse(Checksum.Algorithm.SHA256.name());
            builder.checksum(new Checksum(
                    requiredString(item, ATTR_CHECKSUM),
                    Checksum.Algorithm.valueOf(algorithm)
            ));
        }
        if (stringValue(item, ATTR_BUCKET_NAME) != null && stringValue(item, ATTR_OBJECT_KEY) != null) {
            builder.storageLocation(StorageLocation.of(
                    requiredString(item, ATTR_BUCKET_NAME),
                    requiredString(item, ATTR_OBJECT_KEY)
            ));
        }
        if (stringValue(item, ATTR_KMS_KEY_ID) != null) {
            builder.encryptionMetadata(new EncryptionMetadata(
                    requiredString(item, ATTR_KMS_KEY_ID),
                    EncryptionMetadata.EncryptionAlgorithm.AES_256_GCM,
                    booleanValue(item, ATTR_IS_ENCRYPTED)
            ));
        }
        return builder.build();
    }

    private void validateEvent(DomainEvent event) {
        if (eventContractValidator != null) {
            eventContractValidator.validate(event);
        }
    }

    private static Map<String, AttributeValue> currentStateKey(FileId fileId) {
        return Map.of(PK, s("FILE#" + fileId), SK, s(CURRENT_STATE_SK));
    }

    private static String eventId(DomainEvent event) {
        if (event instanceof FileUploadedEvent uploadedEvent) {
            return uploadedEvent.eventId().toString();
        }
        throw new IllegalArgumentException("Unsupported outbox event: " + event.getClass().getName());
    }

    static String outboxShard(String aggregateId) {
        byte[] digest = Sha256Digest.digest(aggregateId.getBytes(StandardCharsets.UTF_8));
        return String.format("%02x", (digest[0] & 0xff) % OUTBOX_SHARD_COUNT);
    }

    private static void putOptionalString(Map<String, AttributeValue> item, String name, String value) {
        if (value != null && !value.isBlank()) {
            item.put(name, s(value));
        }
    }

    private static String readFileId(Map<String, AttributeValue> item) {
        String fileId = stringValue(item, ATTR_FILE_ID);
        return fileId != null ? fileId : requiredString(item, PK).replaceFirst("^FILE#", "");
    }

    private static String requiredString(Map<String, AttributeValue> item, String name) {
        return requireValue(stringValue(item, name), name);
    }

    private static String requiredNumber(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return requireValue(value == null ? null : value.n(), name);
    }

    private static String requireValue(String value, String name) {
        if (value == null) {
            throw new IllegalStateException("Missing DynamoDB attribute: " + name);
        }
        return value;
    }

    private static String firstString(Map<String, AttributeValue> item, String... names) {
        for (String name : names) {
            String value = stringValue(item, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String stringValue(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null ? null : value.s();
    }

    private static Set<String> stringSet(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null || value.ss() == null ? Set.of() : Set.copyOf(value.ss());
    }

    private static boolean booleanValue(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value != null && Boolean.TRUE.equals(value.bool());
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue n(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }

    private static AttributeValue bool(boolean value) {
        return AttributeValue.builder().bool(value).build();
    }
}
