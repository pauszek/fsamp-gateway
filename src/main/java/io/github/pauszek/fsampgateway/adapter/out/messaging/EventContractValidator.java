package io.github.pauszek.fsampgateway.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.github.pauszek.fsampgateway.domain.event.DomainEvent;
import io.github.pauszek.fsampgateway.domain.exception.EventSerializationException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Component
public class EventContractValidator {

    private static final String SCHEMA_RESOURCE = "schema/event.schema.json";

    private final ObjectMapper objectMapper;
    private final JsonSchema schema;

    public EventContractValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schema = loadSchema();
    }

    public void validate(DomainEvent event) {
        try {
            JsonNode document = objectMapper.valueToTree(event);
            Set<ValidationMessage> violations = schema.validate(document);
            if (!violations.isEmpty()) {
                throw new EventSerializationException(
                        "Event violates FSAMP schema 1.2.0: " + violations,
                        null
                );
            }
        } catch (IllegalArgumentException e) {
            throw new EventSerializationException("Failed to serialize event for validation", e);
        }
    }

    private static JsonSchema loadSchema() {
        ClassPathResource resource = new ClassPathResource(SCHEMA_RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException("Required event schema is missing from the runtime: " + SCHEMA_RESOURCE);
        }
        try (InputStream input = resource.getInputStream()) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(input);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load event schema: " + SCHEMA_RESOURCE, e);
        }
    }
}
