package io.github.pauszek.fsampgateway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pauszek.fsampgateway.adapter.out.messaging.SnsEventPublisherAdapter;
import io.github.pauszek.fsampgateway.adapter.out.messaging.SnsPublisherProperties;
import io.github.pauszek.fsampgateway.domain.event.FilePayload;
import io.github.pauszek.fsampgateway.domain.event.FileUploadedEvent;
import io.github.pauszek.fsampgateway.domain.event.SecurityPayload;
import io.github.pauszek.fsampgateway.domain.event.StoragePayload;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@DisplayName("SnsEventPublisherAdapter Integration Tests")
class SnsEventPublisherAdapterIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private SnsPublisherProperties snsProperties;
    private SnsEventPublisherAdapter snsEventPublisherAdapter;

    @BeforeEach
    void setUp() {
        snsProperties = new SnsPublisherProperties();
        snsProperties.setFileEventsTopicArn(topicArn);

        snsEventPublisherAdapter = new SnsEventPublisherAdapter(snsClient, objectMapper, snsProperties);

        String queueArn = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build())
                .attributes()
                .get(QueueAttributeName.QUEUE_ARN);

        String queuePolicy = "{\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"sns.amazonaws.com\"},"
                + "\"Action\":\"sqs:SendMessage\",\"Resource\":\"" + queueArn + "\","
                + "\"Condition\":{\"ArnEquals\":{\"aws:SourceArn\":\"" + topicArn + "\"}}}]}";
        sqsClient.setQueueAttributes(builder -> builder
                .queueUrl(queueUrl)
                .attributes(java.util.Map.of(QueueAttributeName.POLICY, queuePolicy)));

        snsClient.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .build());

        purgeQueue();
    }

    private void purgeQueue() {
        ReceiveMessageResponse response;
        do {
            response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(0)
                    .build());
            for (Message msg : response.messages()) {
                sqsClient.deleteMessage(builder -> builder
                        .queueUrl(queueUrl)
                        .receiptHandle(msg.receiptHandle()));
            }
        } while (!response.messages().isEmpty());
    }

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @DisplayName("should publish FileUploadedEvent to SNS topic")
        void shouldPublishFileUploadedEventToSnsTopic() {
            FileUploadedEvent event = createTestFileUploadedEvent();

            String messageId = snsEventPublisherAdapter.publish(event);

            assertThat(messageId).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("should receive published event in subscribed SQS queue")
        void shouldReceivePublishedEventInSubscribedSqsQueue() {
            FileUploadedEvent event = createTestFileUploadedEvent();

            snsEventPublisherAdapter.publish(event);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1)
                        .build());

                List<Message> messages = response.messages();
                assertThat(messages).isNotEmpty();

                String snsEnvelope = messages.get(0).body();
                assertThat(snsEnvelope).contains("FILE_UPLOADED");
            });
        }

        @Test
        @DisplayName("should include event type in published message")
        void shouldIncludeEventTypeInPublishedMessage() {
            FileUploadedEvent event = createTestFileUploadedEvent();

            snsEventPublisherAdapter.publish(event);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1)
                        .build());

                assertThat(response.messages()).isNotEmpty();
                String messageBody = response.messages().get(0).body();
                assertThat(messageBody).contains("FILE_UPLOADED");
            });
        }

        @Test
        @DisplayName("should include file metadata in published message")
        void shouldIncludeFileMetadataInPublishedMessage() {
            String uniqueFilename = "unique-test-file-" + UUID.randomUUID() + ".pdf";
            FileUploadedEvent event = new FileUploadedEvent(
                    FileUploadedEvent.SCHEMA_VERSION,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    Instant.now(),
                    "fsamp-gateway",
                    "FILE_UPLOADED",
                    FilePayload.of(uniqueFilename, 2048L, "application/pdf", "sha256hash123"),
                    StoragePayload.of(TEST_BUCKET, "files/test/file.pdf"),
                    SecurityPayload.of(true, "AES-256-GCM", "arn:aws:kms:us-west-2:000000000000:key/test")
            );

            snsEventPublisherAdapter.publish(event);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1)
                        .build());

                assertThat(response.messages()).isNotEmpty();
                String messageBody = response.messages().get(0).body();
                assertThat(messageBody).contains(uniqueFilename);
            });
        }
    }

    @Nested
    @DisplayName("publishWithRetry")
    class PublishWithRetry {

        @Test
        @DisplayName("should publish event using retry mechanism")
        void shouldPublishEventUsingRetryMechanism() {
            FileUploadedEvent event = createTestFileUploadedEvent();

            String messageId = snsEventPublisherAdapter.publishWithRetry(event, 3);

            assertThat(messageId).isNotNull().isNotBlank();
        }
    }

    private FileUploadedEvent createTestFileUploadedEvent() {
        FilePayload filePayload = FilePayload.of(
                "integration-test.pdf",
                1024L,
                "application/pdf",
                "sha256hashvalue"
        );

        StoragePayload storagePayload = StoragePayload.of(TEST_BUCKET, "files/test/integration-test.pdf");
        SecurityPayload securityPayload = SecurityPayload.of(
                true,
                "AES-256-GCM",
                "arn:aws:kms:us-west-2:000000000000:key/test-key"
        );

        return new FileUploadedEvent(
                FileUploadedEvent.SCHEMA_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                "fsamp-gateway",
                "FILE_UPLOADED",
                filePayload,
                storagePayload,
                securityPayload
        );
    }
}
