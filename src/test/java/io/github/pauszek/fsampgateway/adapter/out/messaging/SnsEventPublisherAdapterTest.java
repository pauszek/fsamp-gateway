package io.github.pauszek.fsampgateway.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pauszek.fsampgateway.domain.event.DomainEvent;
import io.github.pauszek.fsampgateway.domain.event.FilePayload;
import io.github.pauszek.fsampgateway.domain.event.FileUploadedEvent;
import io.github.pauszek.fsampgateway.domain.event.SecurityPayload;
import io.github.pauszek.fsampgateway.domain.event.StoragePayload;
import io.github.pauszek.fsampgateway.domain.exception.EventPublishException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SnsEventPublisherAdapter")
class SnsEventPublisherAdapterTest {

    @Mock
    private SnsClient snsClient;

    @Mock
    private ObjectMapper objectMapper;

    private SnsPublisherProperties properties;

    private SnsEventPublisherAdapter adapter;

    @Captor
    private ArgumentCaptor<PublishRequest> requestCaptor;

    private static final String TOPIC_ARN = "arn:aws:sns:eu-west-1:123456789:file-events";
    private static final String MESSAGE_ID = "msg-123";

    @BeforeEach
    void setUp() {
        properties = new SnsPublisherProperties();
        properties.setFileEventsTopicArn(TOPIC_ARN);
        adapter = new SnsEventPublisherAdapter(snsClient, objectMapper, properties);
    }

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @DisplayName("should publish FileUploadedEvent successfully")
        void shouldPublishFileUploadedEventSuccessfully() throws Exception {
            // given
            FileUploadedEvent event = createFileUploadedEvent();
            given(objectMapper.writeValueAsString(event)).willReturn("{\"fileId\":\"123\"}");
            given(snsClient.publish(any(PublishRequest.class)))
                    .willReturn(PublishResponse.builder().messageId(MESSAGE_ID).build());

            // when
            String result = adapter.publish(event);

            // then
            assertThat(result).isEqualTo(MESSAGE_ID);
            then(snsClient).should().publish(requestCaptor.capture());
            assertThat(requestCaptor.getValue().topicArn()).isEqualTo(TOPIC_ARN);
        }

        @Test
        @DisplayName("should include eventType in message attributes")
        void shouldIncludeEventTypeInMessageAttributes() throws Exception {
            // given
            FileUploadedEvent event = createFileUploadedEvent();
            given(objectMapper.writeValueAsString(event)).willReturn("{\"fileId\":\"123\"}");
            given(snsClient.publish(any(PublishRequest.class)))
                    .willReturn(PublishResponse.builder().messageId(MESSAGE_ID).build());

            // when
            adapter.publish(event);

            // then
            then(snsClient).should().publish(requestCaptor.capture());
            var attributes = requestCaptor.getValue().messageAttributes();
            assertThat(attributes).containsKey("eventType");
            assertThat(attributes.get("eventType").stringValue()).isEqualTo("FILE_UPLOADED");
        }

        @Test
        @DisplayName("should include correlationId in message attributes")
        void shouldIncludeCorrelationIdInMessageAttributes() throws Exception {
            // given
            FileUploadedEvent event = createFileUploadedEvent();
            given(objectMapper.writeValueAsString(event)).willReturn("{\"fileId\":\"123\"}");
            given(snsClient.publish(any(PublishRequest.class)))
                    .willReturn(PublishResponse.builder().messageId(MESSAGE_ID).build());

            // when
            adapter.publish(event);

            // then
            then(snsClient).should().publish(requestCaptor.capture());
            var attributes = requestCaptor.getValue().messageAttributes();
            assertThat(attributes).containsKey("correlationId");
            assertThat(attributes.get("correlationId").stringValue())
                    .isEqualTo("00000000-0000-0000-0000-000000000123");
        }

        @Test
        @DisplayName("should include mimeType in message attributes")
        void shouldIncludeMimeTypeInMessageAttributes() throws Exception {
            // given
            FileUploadedEvent event = createFileUploadedEvent();
            given(objectMapper.writeValueAsString(event)).willReturn("{\"fileId\":\"123\"}");
            given(snsClient.publish(any(PublishRequest.class)))
                    .willReturn(PublishResponse.builder().messageId(MESSAGE_ID).build());

            // when
            adapter.publish(event);

            // then
            then(snsClient).should().publish(requestCaptor.capture());
            var attributes = requestCaptor.getValue().messageAttributes();
            assertThat(attributes).containsKey("mimeType");
            assertThat(attributes.get("mimeType").stringValue()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("should throw EventPublishException on SnsException")
        void shouldThrowEventPublishExceptionOnSnsError() throws Exception {
            // given
            FileUploadedEvent event = createFileUploadedEvent();
            given(objectMapper.writeValueAsString(event)).willReturn("{\"fileId\":\"123\"}");
            given(snsClient.publish(any(PublishRequest.class)))
                    .willThrow(SnsException.builder().message("Access Denied").build());

            // when/then
            assertThatThrownBy(() -> adapter.publish(event))
                    .isInstanceOf(EventPublishException.class)
                    .hasMessageContaining("Failed to publish event to SNS");
        }

        @Test
        @DisplayName("should throw EventPublishException on JSON serialization failure")
        void shouldThrowEventPublishExceptionOnJsonError() throws Exception {
            // given
            FileUploadedEvent event = createFileUploadedEvent();
            given(objectMapper.writeValueAsString(event))
                    .willThrow(new JsonProcessingException("Serialization failed") {});

            // when/then
            assertThatThrownBy(() -> adapter.publish(event))
                    .isInstanceOf(EventPublishException.class)
                    .hasMessageContaining("Failed to serialize event");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for unknown event types")
        void shouldThrowIllegalArgumentExceptionForUnknownEventTypes() throws Exception {
            // given
            DomainEvent unknownEvent = new DomainEvent() {
                @Override
                public String getEventType() {
                    return "UNKNOWN_EVENT";
                }

                @Override
                public Instant getOccurredAt() {
                    return Instant.now();
                }
            };

            // when/then
            assertThatThrownBy(() -> adapter.publish(unknownEvent))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown event type");
        }

        @Test
        @DisplayName("should serialize event to JSON message body")
        void shouldSerializeEventToJsonMessageBody() throws Exception {
            // given
            FileUploadedEvent event = createFileUploadedEvent();
            String expectedJson = "{\"fileId\":\"file-123\",\"fileName\":\"test.pdf\"}";
            given(objectMapper.writeValueAsString(event)).willReturn(expectedJson);
            given(snsClient.publish(any(PublishRequest.class)))
                    .willReturn(PublishResponse.builder().messageId(MESSAGE_ID).build());

            // when
            adapter.publish(event);

            // then
            then(snsClient).should().publish(requestCaptor.capture());
            assertThat(requestCaptor.getValue().message()).isEqualTo(expectedJson);
        }
    }

    @Nested
    @DisplayName("publishWithRetry")
    class PublishWithRetry {

        @Test
        @DisplayName("should delegate to publish method")
        void shouldDelegateToPublishMethod() throws Exception {
            // given
            FileUploadedEvent event = createFileUploadedEvent();
            given(objectMapper.writeValueAsString(event)).willReturn("{\"fileId\":\"123\"}");
            given(snsClient.publish(any(PublishRequest.class)))
                    .willReturn(PublishResponse.builder().messageId(MESSAGE_ID).build());

            // when
            String result = adapter.publishWithRetry(event, 3);

            // then
            assertThat(result).isEqualTo(MESSAGE_ID);
            then(snsClient).should().publish(any(PublishRequest.class));
        }
    }

    // Helper methods

    private FileUploadedEvent createFileUploadedEvent() {
        FilePayload filePayload = FilePayload.of(
                "test.pdf",
                1024L,
                "application/pdf",
                "sha256hash"
        );
        
        StoragePayload storagePayload = StoragePayload.of("bucket", "key");
        SecurityPayload securityPayload = SecurityPayload.of(true, "AES-256-GCM", "kms-key-id");

        return new FileUploadedEvent(
                FileUploadedEvent.SCHEMA_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000123"),
                Instant.now(),
                "fsamp-gateway",
                "FILE_UPLOADED",
                filePayload,
                storagePayload,
                securityPayload
        );
    }
}
