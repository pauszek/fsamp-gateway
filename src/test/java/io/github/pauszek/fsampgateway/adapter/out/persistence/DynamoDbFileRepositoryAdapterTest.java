package io.github.pauszek.fsampgateway.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.pauszek.fsampgateway.domain.event.FileUploadedEvent;
import io.github.pauszek.fsampgateway.domain.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link DynamoDbFileRepositoryAdapter}.
 * 
 * Tests DynamoDB serialization/deserialization and correct AWS SDK usage
 * with a mocked {@link DynamoDbClient}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoDbFileRepositoryAdapter")
class DynamoDbFileRepositoryAdapterTest {

    private static final String TABLE_NAME = "test-metadata";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Captor
    private ArgumentCaptor<PutItemRequest> putRequestCaptor;

    @Captor
    private ArgumentCaptor<QueryRequest> queryRequestCaptor;

    @Captor
    private ArgumentCaptor<DeleteItemRequest> deleteRequestCaptor;

    @Captor
    private ArgumentCaptor<TransactWriteItemsRequest> transactWriteItemsRequestCaptor;

    private DynamoDbFileRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DynamoDbFileRepositoryAdapter(dynamoDbClient, objectMapper(), TABLE_NAME, "");
    }

    // ========================================================================
    // save()
    // ========================================================================

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("should save pending file with core attributes only")
        void shouldSavePendingFileWithCoreAttributes() {
            // given
            SecureFile file = createPendingFile();
            given(dynamoDbClient.putItem(any(PutItemRequest.class)))
                    .willReturn(PutItemResponse.builder().build());

            // when
            SecureFile result = adapter.save(file);

            // then
            assertThat(result).isEqualTo(file);
            then(dynamoDbClient).should().putItem(putRequestCaptor.capture());

            PutItemRequest request = putRequestCaptor.getValue();
            assertThat(request.tableName()).isEqualTo(TABLE_NAME);

            Map<String, AttributeValue> item = request.item();
            assertThat(item.get("PK").s()).isEqualTo("FILE#" + file.getId());
            assertThat(item.get("SK").s()).isEqualTo("TS#" + file.getAuditInfo().createdAt());
            assertThat(item.get("fileId").s()).isEqualTo(file.getId().toString());
            assertThat(item.get("correlationId").s()).isEqualTo(file.getCorrelationId().value());
            assertThat(item.get("fileName").s()).isEqualTo(file.getFileName().value());
            assertThat(item.get("mimeType").s()).isEqualTo(file.getMimeType().value());
            assertThat(item.get("fileSizeBytes").n()).isEqualTo(String.valueOf(file.getSize().bytes()));
            assertThat(item.get("status").s()).isEqualTo("PENDING");
            assertThat(item.get("GSI1PK").s()).isEqualTo("STATUS#PENDING");
            assertThat(item.get("GSI1SK").s()).isEqualTo(file.getAuditInfo().createdAt().toString());
            assertThat(item.get("createdBy").s()).isEqualTo("user-123");

            // Nullable fields should not be present for pending files
            assertThat(item).doesNotContainKeys("checksumSHA256", "bucketName", "kmsKeyId");
        }

        @Test
        @DisplayName("should save uploaded file with all attributes")
        void shouldSaveUploadedFileWithAllAttributes() {
            // given
            SecureFile file = createUploadedFile();
            given(dynamoDbClient.putItem(any(PutItemRequest.class)))
                    .willReturn(PutItemResponse.builder().build());

            // when
            adapter.save(file);

            // then
            then(dynamoDbClient).should().putItem(putRequestCaptor.capture());
            Map<String, AttributeValue> item = putRequestCaptor.getValue().item();

            assertThat(item.get("status").s()).isEqualTo("UPLOADED");
            assertThat(item.get("checksumSHA256").s()).isNotNull();
            assertThat(item.get("checksumAlgorithm").s()).isEqualTo("SHA256");
            assertThat(item.get("bucketName").s()).isEqualTo("test-bucket");
            assertThat(item.get("objectKey").s()).isEqualTo("test-key");
            assertThat(item.get("kmsKeyId").s()).isEqualTo("alias/test-kms-key");
            assertThat(item.get("encryptionAlgorithm").s()).isEqualTo("AES_256_GCM");
            assertThat(item.get("isEncrypted").bool()).isTrue();
        }

        @Test
        @DisplayName("should propagate DynamoDB exceptions")
        void shouldPropagateDynamoDbExceptions() {
            // given
            SecureFile file = createPendingFile();
            given(dynamoDbClient.putItem(any(PutItemRequest.class)))
                    .willThrow(DynamoDbException.builder()
                            .message("Provisioned throughput exceeded").build());

            // when/then
            assertThatThrownBy(() -> adapter.save(file))
                    .isInstanceOf(DynamoDbException.class)
                    .hasMessageContaining("Provisioned throughput exceeded");
        }
    }

    // ========================================================================
    // saveWithOutbox()
    // ========================================================================

    @Nested
    @DisplayName("saveWithOutbox")
    class SaveWithOutbox {

        @Test
        @DisplayName("should persist metadata and outbox event transactionally when outbox table is configured")
        void shouldPersistMetadataAndOutboxEventTransactionally() {
            // given
            SecureFile file = createUploadedFile();
            FileUploadedEvent event = FileUploadedEvent.from(file);
            DynamoDbFileRepositoryAdapter outboxAdapter =
                    new DynamoDbFileRepositoryAdapter(dynamoDbClient, objectMapper(), TABLE_NAME, "test-outbox");
            given(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .willReturn(TransactWriteItemsResponse.builder().build());

            // when
            SecureFile result = outboxAdapter.saveWithOutbox(file, event);

            // then
            assertThat(result).isEqualTo(file);
            then(dynamoDbClient).should().transactWriteItems(transactWriteItemsRequestCaptor.capture());

            TransactWriteItemsRequest request = transactWriteItemsRequestCaptor.getValue();
            assertThat(request.transactItems()).hasSize(2);

            Put metadataPut = request.transactItems().get(0).put();
            assertThat(metadataPut.tableName()).isEqualTo(TABLE_NAME);
            assertThat(metadataPut.item().get("PK").s()).isEqualTo("FILE#" + file.getId());

            Put outboxPut = request.transactItems().get(1).put();
            assertThat(outboxPut.tableName()).isEqualTo("test-outbox");
            assertThat(outboxPut.conditionExpression()).isEqualTo("attribute_not_exists(PK) AND attribute_not_exists(SK)");
            assertThat(outboxPut.item().get("PK").s()).isEqualTo("OUTBOX#FileUpload");
            assertThat(outboxPut.item().get("eventType").s()).isEqualTo("FILE_UPLOADED");
            assertThat(outboxPut.item().get("aggregateId").s()).isEqualTo(file.getId().toString());
            assertThat(outboxPut.item().get("status").s()).isEqualTo("PENDING");
            assertThat(outboxPut.item().get("payload").s()).contains("\"fileId\":\"" + file.getId() + "\"");
        }
    }

    // ========================================================================
    // findById()
    // ========================================================================

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return file when found in DynamoDB")
        void shouldReturnFileWhenFound() {
            // given
            SecureFile original = createUploadedFile();
            mockQueryReturning(original);

            // when
            Optional<SecureFile> result = adapter.findById(original.getId());

            // then
            assertThat(result).isPresent();
            SecureFile found = result.get();
            assertThat(found.getId()).isEqualTo(original.getId());
            assertThat(found.getFileName().value()).isEqualTo(original.getFileName().value());
            assertThat(found.getMimeType().value()).isEqualTo(original.getMimeType().value());
            assertThat(found.getStatus()).isEqualTo(original.getStatus());
            assertThat(found.getCorrelationId().value()).isEqualTo(original.getCorrelationId().value());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            // given
            given(dynamoDbClient.query(any(QueryRequest.class)))
                    .willReturn(QueryResponse.builder().items(List.of()).build());

            // when
            Optional<SecureFile> result = adapter.findById(FileId.generate());

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should query with correct key condition and descending sort")
        void shouldQueryWithCorrectParameters() {
            // given
            FileId fileId = FileId.generate();
            given(dynamoDbClient.query(any(QueryRequest.class)))
                    .willReturn(QueryResponse.builder().items(List.of()).build());

            // when
            adapter.findById(fileId);

            // then
            then(dynamoDbClient).should().query(queryRequestCaptor.capture());
            QueryRequest request = queryRequestCaptor.getValue();

            assertThat(request.tableName()).isEqualTo(TABLE_NAME);
            assertThat(request.keyConditionExpression()).isEqualTo("#pk = :pkVal");
            assertThat(request.expressionAttributeNames()).containsEntry("#pk", "PK");
            assertThat(request.expressionAttributeValues())
                    .containsEntry(":pkVal", AttributeValue.fromS("FILE#" + fileId));
            assertThat(request.scanIndexForward()).isFalse();
            assertThat(request.limit()).isEqualTo(1);
        }

        @Test
        @DisplayName("should deserialize pending file without optional fields")
        void shouldDeserializePendingFileWithoutOptionalFields() {
            // given
            SecureFile pendingFile = createPendingFile();
            mockQueryReturning(pendingFile);

            // when
            Optional<SecureFile> result = adapter.findById(pendingFile.getId());

            // then
            assertThat(result).isPresent();
            SecureFile found = result.get();
            assertThat(found.getChecksum()).isNull();
            assertThat(found.getStorageLocation()).isNull();
            assertThat(found.getEncryptionMetadata()).isNull();
            assertThat(found.getStatus()).isEqualTo(FileStatus.PENDING);
        }

        @Test
        @DisplayName("should deserialize uploaded file with all optional fields")
        void shouldDeserializeUploadedFileWithAllFields() {
            // given
            SecureFile uploadedFile = createUploadedFile();
            mockQueryReturning(uploadedFile);

            // when
            Optional<SecureFile> result = adapter.findById(uploadedFile.getId());

            // then
            assertThat(result).isPresent();
            SecureFile found = result.get();
            assertThat(found.getChecksum()).isNotNull();
            assertThat(found.getChecksum().algorithm()).isEqualTo(Checksum.Algorithm.SHA256);
            assertThat(found.getStorageLocation()).isNotNull();
            assertThat(found.getStorageLocation().bucketName()).isEqualTo("test-bucket");
            assertThat(found.getStorageLocation().objectKey()).isEqualTo("test-key");
            assertThat(found.getEncryptionMetadata()).isNotNull();
            assertThat(found.getEncryptionMetadata().kmsKeyId()).isEqualTo("alias/test-kms-key");
            assertThat(found.getEncryptionMetadata().encrypted()).isTrue();
        }
    }

    // ========================================================================
    // delete()
    // ========================================================================

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should find file and delete by PK+SK")
        void shouldFindAndDeleteByPkSk() {
            // given
            SecureFile file = createPendingFile();
            mockQueryReturning(file);
            given(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
                    .willReturn(DeleteItemResponse.builder().build());

            // when
            adapter.delete(file.getId());

            // then
            then(dynamoDbClient).should().query(any(QueryRequest.class));
            then(dynamoDbClient).should().deleteItem(deleteRequestCaptor.capture());

            DeleteItemRequest request = deleteRequestCaptor.getValue();
            assertThat(request.tableName()).isEqualTo(TABLE_NAME);
            assertThat(request.key().get("PK").s()).isEqualTo("FILE#" + file.getId());
            assertThat(request.key().get("SK").s())
                    .isEqualTo("TS#" + file.getAuditInfo().createdAt());
        }

        @Test
        @DisplayName("should not call deleteItem when file not found")
        void shouldNotDeleteWhenNotFound() {
            // given
            given(dynamoDbClient.query(any(QueryRequest.class)))
                    .willReturn(QueryResponse.builder().items(List.of()).build());

            // when
            adapter.delete(FileId.generate());

            // then
            then(dynamoDbClient).should(never()).deleteItem(any(DeleteItemRequest.class));
        }
    }

    // ========================================================================
    // exists()
    // ========================================================================

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("should return true when count > 0")
        void shouldReturnTrueWhenCountGreaterThanZero() {
            // given
            given(dynamoDbClient.query(any(QueryRequest.class)))
                    .willReturn(QueryResponse.builder().count(1).build());

            // when
            boolean result = adapter.exists(FileId.generate());

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when count is 0")
        void shouldReturnFalseWhenCountIsZero() {
            // given
            given(dynamoDbClient.query(any(QueryRequest.class)))
                    .willReturn(QueryResponse.builder().count(0).build());

            // when
            boolean result = adapter.exists(FileId.generate());

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should use SELECT COUNT for efficiency")
        void shouldUseSelectCount() {
            // given
            given(dynamoDbClient.query(any(QueryRequest.class)))
                    .willReturn(QueryResponse.builder().count(0).build());

            // when
            adapter.exists(FileId.generate());

            // then
            then(dynamoDbClient).should().query(queryRequestCaptor.capture());
            assertThat(queryRequestCaptor.getValue().select()).isEqualTo(Select.COUNT);
            assertThat(queryRequestCaptor.getValue().limit()).isEqualTo(1);
        }
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    private SecureFile createPendingFile() {
        return SecureFile.createPending(
                FileName.of("test-document.pdf"),
                MimeType.of("application/pdf"),
                FileSize.of(1024L),
                CorrelationId.generate(),
                "user-123"
        );
    }

    private SecureFile createUploadedFile() {
        SecureFile pending = createPendingFile();
        return pending.markAsUploaded(
                StorageLocation.of("test-bucket", "test-key"),
                new EncryptionMetadata("alias/test-kms-key", EncryptionMetadata.EncryptionAlgorithm.AES_256_GCM, true),
                Checksum.sha256("a".repeat(64))
        );
    }

    /**
     * Mocks a DynamoDB Query that returns the given SecureFile serialized as a DynamoDB item.
     */
    private void mockQueryReturning(SecureFile file) {
        Map<String, AttributeValue> item = new HashMap<>();

        // Keys
        item.put("PK", s("FILE#" + file.getId()));
        item.put("SK", s("TS#" + file.getAuditInfo().createdAt()));
        item.put("fileId", s(file.getId().toString()));

        // Core attributes
        item.put("correlationId", s(file.getCorrelationId().value()));
        item.put("fileName", s(file.getFileName().value()));
        item.put("mimeType", s(file.getMimeType().value()));
        item.put("fileSizeBytes", n(file.getSize().bytes()));
        item.put("status", s(file.getStatus().name()));
        item.put("GSI1PK", s("STATUS#" + file.getStatus().name()));
        item.put("GSI1SK", s(file.getAuditInfo().createdAt().toString()));
        item.put("createdBy", s(file.getAuditInfo().createdBy()));
        item.put("createdAt", s(file.getAuditInfo().createdAt().toString()));
        item.put("updatedAt", s(file.getAuditInfo().updatedAt().toString()));

        // Optional fields
        if (file.getChecksum() != null) {
            item.put("checksumSHA256", s(file.getChecksum().value()));
            item.put("checksumAlgorithm", s(file.getChecksum().algorithm().name()));
        }
        if (file.getStorageLocation() != null) {
            item.put("bucketName", s(file.getStorageLocation().bucketName()));
            item.put("objectKey", s(file.getStorageLocation().objectKey()));
        }
        if (file.getEncryptionMetadata() != null) {
            item.put("kmsKeyId", s(file.getEncryptionMetadata().kmsKeyId()));
            item.put("encryptionAlgorithm", s(file.getEncryptionMetadata().algorithm().name()));
            item.put("isEncrypted", AttributeValue.builder().bool(file.getEncryptionMetadata().encrypted()).build());
        }

        given(dynamoDbClient.query(any(QueryRequest.class)))
                .willReturn(QueryResponse.builder().items(List.of(item)).build());
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue n(long value) {
        return AttributeValue.builder().n(String.valueOf(value)).build();
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
