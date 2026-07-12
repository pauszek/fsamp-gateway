package io.github.pauszek.fsampgateway.domain.event;

import java.time.Instant;

public interface DomainEvent {

    String getEventType();

    Instant getOccurredAt();
}
