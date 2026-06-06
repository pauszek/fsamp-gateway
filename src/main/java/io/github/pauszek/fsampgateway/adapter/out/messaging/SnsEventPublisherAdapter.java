package io.github.pauszek.fsampgateway.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pauszek.fsampgateway.domain.event.DomainEvent;
import io.github.pauszek.fsampgateway.domain.event.FileUploadedEvent;
import io.github.pauszek.fsampgateway.domain.exception.EventPublishException;
import io.github.pauszek.fsampgateway.domain.exception.EventSerializationException;
import io.github.pauszek.fsampgateway.domain.port.out.EventPublisherPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.util.HashMap;
import java.util.Map;

@Component
public class SnsEventPublisherAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(SnsEventPublisherAdapter.class);
    private static final String CIRCUIT_BREAKER_NAME = "snsPublisher";

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final SnsPublisherProperties properties;

    public SnsEventPublisherAdapter(
            SnsClient snsClient,
            ObjectMapper objectMapper,
            SnsPublisherProperties properties
    ) {
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "publishFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public String publish(DomainEvent event) {
        return doPublish(event);
    }

    @Override
    public String publishWithRetry(DomainEvent event, int maxRetries) {
        return publish(event);
    }

    private String doPublish(DomainEvent event) {
        String topicArn = resolveTopicArn(event);
        
        try {
            String messageBody = objectMapper.writeValueAsString(event);
            Map<String, MessageAttributeValue> attributes = buildMessageAttributes(event);

            PublishRequest request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(messageBody)
                    .messageAttributes(attributes)
                    .build();

            log.info("Publishing event: type={}, topic={}", event.getEventType(), topicArn);

            PublishResponse response = snsClient.publish(request);

            log.info("Event published: messageId={}, type={}", 
                    response.messageId(), event.getEventType());

            return response.messageId();

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", e.getMessage(), e);
            throw new EventSerializationException("Failed to serialize event", e);
        } catch (SnsException e) {
            log.error("Failed to publish event to SNS: {}", e.getMessage(), e);
            throw new EventPublishException("Failed to publish event to SNS", e);
        }
    }

    @SuppressWarnings("unused")
    private String publishFallback(DomainEvent event, Exception e) {
        log.error("Circuit breaker fallback for publish: type={}, error={}", 
                event.getEventType(), e.getMessage());
        throw new EventPublishException("Event publishing service unavailable", e);
    }

    private String resolveTopicArn(DomainEvent event) {
        if (event instanceof FileUploadedEvent) {
            return properties.getFileEventsTopicArn();
        }
        throw new IllegalArgumentException("Unknown event type: " + event.getClass().getName());
    }

    private Map<String, MessageAttributeValue> buildMessageAttributes(DomainEvent event) {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        
        attributes.put("eventType", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(event.getEventType())
                .build());

        if (event instanceof FileUploadedEvent fileEvent) {
            attributes.put("fileId", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(fileEvent.fileId().toString())
                    .build());

            attributes.put("correlationId", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(fileEvent.correlationId().toString())
                    .build());
            
            if (fileEvent.fileMetadata() != null) {
                attributes.put("mimeType", MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(fileEvent.fileMetadata().getMimeType())
                        .build());
            }
        }

        return attributes;
    }
}
