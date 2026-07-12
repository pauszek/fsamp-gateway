package io.github.pauszek.fsampgateway.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.pauszek.fsampgateway.domain.event.DomainEvent;
import io.github.pauszek.fsampgateway.domain.event.FilePayload;
import io.github.pauszek.fsampgateway.domain.event.FileUploadedEvent;
import io.github.pauszek.fsampgateway.domain.event.SecurityPayload;
import io.github.pauszek.fsampgateway.domain.event.StoragePayload;
import io.github.pauszek.fsampgateway.domain.exception.EventSerializationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventContractValidatorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldAcceptCanonicalGatewayEvent() {
        EventContractValidator validator = new EventContractValidator(OBJECT_MAPPER);

        assertThatCode(() -> validator.validate(event("FILE_UPLOADED")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectEventThatViolatesSchema() {
        EventContractValidator validator = new EventContractValidator(OBJECT_MAPPER);

        assertThatThrownBy(() -> validator.validate(event("PROCESSING_FAILED")))
                .isInstanceOf(EventSerializationException.class)
                .hasMessageContaining("schema 1.2.0");
    }

    @Test
    void shouldWrapObjectMappingFailure() {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        DomainEvent event = event("FILE_UPLOADED");
        when(failingMapper.valueToTree(any()))
                .thenThrow(new IllegalArgumentException("cannot serialize"));
        EventContractValidator validator = new EventContractValidator(failingMapper);

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(EventSerializationException.class)
                .hasMessageContaining("Failed to serialize");
    }

    private static FileUploadedEvent event(String eventType) {
        return new FileUploadedEvent(
                FileUploadedEvent.SCHEMA_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-01-15T10:00:00Z"),
                FileUploadedEvent.EVENT_SOURCE,
                eventType,
                FilePayload.of(
                        "test.pdf",
                        1024,
                        "application/pdf",
                        "a".repeat(64)
                ),
                StoragePayload.of("fsamp-bucket", "uploads/test.pdf"),
                SecurityPayload.of(
                        true,
                        "AES/GCM/NoPadding",
                        "arn:aws:kms:eu-central-1:123456789012:key/12345678-1234-1234-1234-123456789012"
                )
        );
    }
}
