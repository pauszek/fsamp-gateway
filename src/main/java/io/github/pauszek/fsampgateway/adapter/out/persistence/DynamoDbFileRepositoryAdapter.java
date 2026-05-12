package io.github.pauszek.fsampgateway.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pauszek.fsampgateway.domain.event.DomainEvent;
import io.github.pauszek.fsampgateway.domain.event.FileUploadedEvent;
import io.github.pauszek.fsampgateway.domain.exception.EventPublishException;
import io.github.pauszek.fsampgateway.domain.model.*;
import io.github.pauszek.fsampgateway.domain.port.out.FileRepositoryPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter - DynamoDB File Repository.
 * 
 * Production implementation of {@link FileRepositoryPort} using AWS DynamoDB.
 * Replaces InMemoryFileRepositoryAdapter for durable persistence.
 * 
 * Table schema (matches Terraform {@code storage} module):
 * <ul>
 *   <li>PK: {@code FILE#<fileId>} - stable file aggregate key</li>
 *   <li>SK: {@code TS#<timestamp>} - ISO-8601 metadata version key</li>
 *   <li>GSI1: {@code STATUS#<status>} → {@code <timestamp>} for status-based queries</li>
 * </ul>
 * 
 * FedRAMP AU-3: All persistence operations are logged with correlation context.
 */
@Repository
@Primary
@Profile("!test")
public class DynamoDbFileRepositoryAdapter implements FileRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbFileRepositoryAdapter.class);

    // Attribute names matching Terraform table definition
    private static final String PK = "PK";
    private static final String SK = "SK";
    private static final String GSI1_PK = "GSI1PK";
    private static final String GSI1_SK = "GSI1SK";
    private static final String ATTR_FILE_ID = "fileId";
    private static final String ATTR_CORRELATION_ID = "correlationId";
    private static final String ATTR_FILE_NAME = "fileName";
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
    private static final String OUTBOX_STATUS_PENDING = FileStatus.PENDING.name();

    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;
    private final String tableName;
    private final String outboxTableName;

    public DynamoDbFileRepositoryAdapter(
            DynamoDbClient dynamoDbClient,
            ObjectMapper objectMapper,
            @Value("${aws.dynamodb.table-name}") String tableName,
            @Value("${aws.dynamodb.outbox-table-name:}") String outboxTableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.objectMapper = objectMapper;
        this.tableName = tableName;
        this.outboxTableName = outboxTableName;
        log.info("DynamoDB file repository initialized: table={}, outboxEnabled={}",
                tableName, supportsTransactionalOutbox());
    }

    @Override
    @CircuitBreaker(name = "dynamoDb")
    @Retry(name = "dynamoDb")
    public SecureFile save(SecureFile file) {
        log.debug("Saving file to DynamoDB: fileId={}, status={}", file.getId(), file.getStatus());

        Map<String, AttributeValue> item = toItem(file);

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
        log.info("File saved to DynamoDB: fileId={}, status={}", file.getId(), file.getStatus());

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

        Map<String, AttributeValue> metadataItem = toItem(file);
        Map<String, AttributeValue> outboxItem = toOutboxItem(file, event);

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder()
                                .put(put -> put
                                        .tableName(tableName)
                                        .item(metadataItem))
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
        log.info("File and outbox event saved to DynamoDB transactionally: fileId={}, eventType={}",
                file.getId(), event.getEventType());
        return file;
    }

    @Override
    @CircuitBreaker(name = "dynamoDb")
    @Retry(name = "dynamoDb")
    public Optional<SecureFile> findById(FileId fileId) {
        log.debug("Finding file in DynamoDB: fileId={}", fileId);

        // Query by PK (FILE#fileId), get the most recent entry
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("#pk = :pkVal")
                .expressionAttributeNames(Map.of("#pk", PK))
                .expressionAttributeValues(Map.of(
                        ":pkVal", AttributeValue.builder().s(filePk(fileId)).build()
                ))
                .scanIndexForward(false) // Descending order - most recent first
                .limit(1)
                .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);

        if (!response.hasItems() || response.items().isEmpty()) {
            log.debug("File not found in DynamoDB: fileId={}", fileId);
            return Optional.empty();
        }

        SecureFile file = fromItem(response.items().getFirst());
        log.debug("File found in DynamoDB: fileId={}, status={}", file.getId(), file.getStatus());
        return Optional.of(file);
    }

    @Override
    @CircuitBreaker(name = "dynamoDb")
    @Retry(name = "dynamoDb")
    public void delete(FileId fileId) {
        log.debug("Deleting file from DynamoDB: fileId={}", fileId);

        // First find the item to get the SK
        Optional<SecureFile> existing = findById(fileId);
        if (existing.isEmpty()) {
            log.debug("File not found for deletion: fileId={}", fileId);
            return;
        }

        SecureFile file = existing.get();
        DeleteItemRequest request = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        PK, AttributeValue.builder().s(filePk(fileId)).build(),
                        SK, AttributeValue.builder().s(timestampSk(file.getAuditInfo().createdAt().toString())).build()
                ))
                .build();

        dynamoDbClient.deleteItem(request);
        log.info("File deleted from DynamoDB: fileId={}", fileId);
    }

    @Override
    public boolean exists(FileId fileId) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("#pk = :pkVal")
                .expressionAttributeNames(Map.of("#pk", PK))
                .expressionAttributeValues(Map.of(
                        ":pkVal", AttributeValue.builder().s(filePk(fileId)).build()
                ))
                .select(Select.COUNT)
                .limit(1)
                .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);
        return response.count() > 0;
    }

    // ========================================================================
    // Serialization: SecureFile → DynamoDB Item
    // ========================================================================

    private Map<String, AttributeValue> toItem(SecureFile file) {
        Map<String, AttributeValue> item = new HashMap<>();

        // Keys
        item.put(PK, s(filePk(file.getId())));
        item.put(SK, s(timestampSk(file.getAuditInfo().createdAt().toString())));

        // Core metadata
        item.put(ATTR_FILE_ID, s(file.getId().toString()));
        item.put(ATTR_CORRELATION_ID, s(file.getCorrelationId().value()));
        item.put(ATTR_FILE_NAME, s(file.getFileName().value()));
        item.put(ATTR_MIME_TYPE, s(file.getMimeType().value()));
        item.put(ATTR_FILE_SIZE, n(file.getSize().bytes()));
        item.put(ATTR_STATUS, s(file.getStatus().name()));
        item.put(GSI1_PK, s("STATUS#" + file.getStatus().name()));
        item.put(GSI1_SK, s(file.getAuditInfo().createdAt().toString()));

        // Checksum (may be null for PENDING files)
        if (file.getChecksum() != null) {
            item.put(ATTR_CHECKSUM, s(file.getChecksum().value()));
            item.put(ATTR_CHECKSUM_ALGORITHM, s(file.getChecksum().algorithm().name()));
        }

        // Storage location (may be null for PENDING files)
        if (file.getStorageLocation() != null) {
            item.put(ATTR_BUCKET_NAME, s(file.getStorageLocation().bucketName()));
            item.put(ATTR_OBJECT_KEY, s(file.getStorageLocation().objectKey()));
        }

        // Encryption metadata (may be null for PENDING files)
        if (file.getEncryptionMetadata() != null) {
            item.put(ATTR_KMS_KEY_ID, s(file.getEncryptionMetadata().kmsKeyId()));
            item.put(ATTR_ENCRYPTION_ALGORITHM, s(file.getEncryptionMetadata().algorithm().name()));
            item.put(ATTR_IS_ENCRYPTED, bool(file.getEncryptionMetadata().encrypted()));
        }

        // Audit info
        item.put(ATTR_CREATED_BY, s(file.getAuditInfo().createdBy()));
        item.put(ATTR_CREATED_AT, s(file.getAuditInfo().createdAt().toString()));
        item.put(ATTR_UPDATED_AT, s(file.getAuditInfo().updatedAt().toString()));

        return item;
    }

    private Map<String, AttributeValue> toOutboxItem(SecureFile file, DomainEvent event) {
        try {
            String eventId = event instanceof FileUploadedEvent fileEvent
                    ? fileEvent.eventId().toString()
                    : file.getId().toString() + "-" + event.getOccurredAt().toEpochMilli();
            String eventType = event.getEventType();
            String aggregateType = "FileUpload";
            String createdAt = event.getOccurredAt().toString();

            Map<String, AttributeValue> item = new HashMap<>();
            item.put(PK, s("OUTBOX#" + aggregateType));
            item.put(SK, s("EVENT#" + eventId));
            item.put("eventId", s(eventId));
            item.put("eventType", s(eventType));
            item.put("aggregateId", s(file.getId().toString()));
            item.put("aggregateType", s(aggregateType));
            item.put("payload", s(objectMapper.writeValueAsString(event)));
            item.put(ATTR_STATUS, s(OUTBOX_STATUS_PENDING));
            item.put(ATTR_CREATED_AT, s(createdAt));
            item.put("retryCount", n(0));
            item.put("messageGroupId", s(file.getId().toString()));
            item.put(GSI1_PK, s("STATUS#" + OUTBOX_STATUS_PENDING));
            item.put(GSI1_SK, s(createdAt));
            return item;
        } catch (JsonProcessingException e) {
            throw new EventPublishException("Failed to serialize event for outbox", e);
        }
    }

    // ========================================================================
    // Deserialization: DynamoDB Item → SecureFile
    // ========================================================================

    private SecureFile fromItem(Map<String, AttributeValue> item) {
        SecureFile.Builder builder = SecureFile.builder()
                .id(FileId.of(readFileId(item)))
                .correlationId(CorrelationId.of(item.get(ATTR_CORRELATION_ID).s()))
                .fileName(FileName.of(item.get(ATTR_FILE_NAME).s()))
                .mimeType(MimeType.of(item.get(ATTR_MIME_TYPE).s()))
                .size(FileSize.of(Long.parseLong(item.get(ATTR_FILE_SIZE).n())))
                .status(FileStatus.valueOf(item.get(ATTR_STATUS).s()))
                .auditInfo(new AuditInfo(
                        item.get(ATTR_CREATED_BY).s(),
                        Instant.parse(item.get(ATTR_CREATED_AT).s()),
                        Instant.parse(item.get(ATTR_UPDATED_AT).s())
                ));

        // Optional fields
        if (item.containsKey(ATTR_CHECKSUM) && item.get(ATTR_CHECKSUM).s() != null) {
            Checksum.Algorithm algo = item.containsKey(ATTR_CHECKSUM_ALGORITHM)
                    ? Checksum.Algorithm.valueOf(item.get(ATTR_CHECKSUM_ALGORITHM).s())
                    : Checksum.Algorithm.SHA256;
            builder.checksum(new Checksum(item.get(ATTR_CHECKSUM).s(), algo));
        }

        if (item.containsKey(ATTR_BUCKET_NAME) && item.containsKey(ATTR_OBJECT_KEY)) {
            builder.storageLocation(StorageLocation.of(
                    item.get(ATTR_BUCKET_NAME).s(),
                    item.get(ATTR_OBJECT_KEY).s()
            ));
        }

        if (item.containsKey(ATTR_KMS_KEY_ID)) {
            EncryptionMetadata.EncryptionAlgorithm algo = item.containsKey(ATTR_ENCRYPTION_ALGORITHM)
                    ? EncryptionMetadata.EncryptionAlgorithm.valueOf(item.get(ATTR_ENCRYPTION_ALGORITHM).s())
                    : EncryptionMetadata.EncryptionAlgorithm.AES_256_GCM;
            builder.encryptionMetadata(new EncryptionMetadata(
                    item.get(ATTR_KMS_KEY_ID).s(),
                    algo,
                    item.containsKey(ATTR_IS_ENCRYPTED) && item.get(ATTR_IS_ENCRYPTED).bool()
            ));
        }

        return builder.build();
    }

    // ========================================================================
    // DynamoDB AttributeValue helpers
    // ========================================================================

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue n(long value) {
        return AttributeValue.builder().n(String.valueOf(value)).build();
    }

    private static AttributeValue bool(boolean value) {
        return AttributeValue.builder().bool(value).build();
    }

    private static String filePk(FileId fileId) {
        return "FILE#" + fileId;
    }

    private static String timestampSk(String timestamp) {
        return "TS#" + timestamp;
    }

    private static String readFileId(Map<String, AttributeValue> item) {
        AttributeValue fileId = item.get(ATTR_FILE_ID);
        if (fileId != null && fileId.s() != null) {
            return fileId.s();
        }
        return item.get(PK).s().replaceFirst("^FILE#", "");
    }
}
