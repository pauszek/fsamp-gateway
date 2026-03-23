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

/**
 * Integration tests for SnsEventPublisherAdapter with real LocalStack SNS/SQS.
 * 
 * Uses SNS→SQS subscription to verify messages are published correctly.
 */
@DisplayName("SnsEventPublisherAdapter Integration Tests")
class SnsEventPublisherAdapterIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private SnsPublisherProperties snsProperties;
    private SnsEventPublisherAdapter snsEventPublisherAdapter;

    @BeforeEach
    void setUp() {
        // Configure properties for LocalStack
        snsProperties = new SnsPublisherProperties();
        snsProperties.setFileEventsTopicArn(topicArn);
        
        snsEventPublisherAdapter = new SnsEventPublisherAdapter(snsClient, objectMapper, snsProperties);

        // Get SQS queue ARN (needed for SNS subscription)
        String queueArn = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build())
                .attributes()
                .get(QueueAttributeName.QUEUE_ARN);

        // Allow SNS to send messages to SQS (required when ENFORCE_IAM=1)
        String queuePolicy = "{\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"sns.amazonaws.com\"},"
                + "\"Action\":\"sqs:SendMessage\",\"Resource\":\"" + queueArn + "\","
                + "\"Condition\":{\"ArnEquals\":{\"aws:SourceArn\":\"" + topicArn + "\"}}}]}";
        sqsClient.setQueueAttributes(builder -> builder
                .queueUrl(queueUrl)
                .attributes(java.util.Map.of(QueueAttributeName.POLICY, queuePolicy)));

        // Subscribe SQS queue to SNS topic for message verification
        snsClient.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .build());

        // Purge queue before each test to avoid stale messages from previous tests
        purgeQueue();
    }

    private void purgeQueue() {
        // Drain all existing messages from the queue
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
            // given
            FileUploadedEvent event = createTestFileUploadedEvent();

            // when
            String messageId = snsEventPublisherAdapter.publish(event);

            // then
            assertThat(messageId).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("should receive published event in subscribed SQS queue")
        void shouldReceivePublishedEventInSubscribedSqsQueue() {
            // given
            FileUploadedEvent event = createTestFileUploadedEvent();

            // when
            snsEventPublisherAdapter.publish(event);

            // then - message should arrive in SQS queue
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1)
                        .build());

                List<Message> messages = response.messages();
                assertThat(messages).isNotEmpty();
                
                // SNS wraps the message in its own envelope
                String snsEnvelope = messages.get(0).body();
                assertThat(snsEnvelope).contains("FILE_UPLOADED");
            });
        }

        @Test
        @DisplayName("should include event type in published message")
        void shouldIncludeEventTypeInPublishedMessage() {
            // given
            FileUploadedEvent event = createTestFileUploadedEvent();

            // when
            snsEventPublisherAdapter.publish(event);

            // then
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
            // given
            String uniqueFilename = "unique-test-file-" + UUID.randomUUID() + ".pdf";
            FileUploadedEvent event = new FileUploadedEvent(
                    "1.0.0",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    Instant.now(),
                    "fsamp-gateway",
                    "FILE_UPLOADED",
                    FilePayload.of(uniqueFilename, 2048L, "application/pdf", "sha256hash123"),
                    StoragePayload.of(TEST_BUCKET, "files/test/file.pdf"),
                    SecurityPayload.of(true, "AES-256-GCM", "arn:aws:kms:us-west-2:000000000000:key/test")
            );

            // when
            snsEventPublisherAdapter.publish(event);

            // then
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
            // given
            FileUploadedEvent event = createTestFileUploadedEvent();

            // when
            String messageId = snsEventPublisherAdapter.publishWithRetry(event, 3);

            // then
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
                "1.0.0",
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
