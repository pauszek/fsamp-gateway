package io.github.pauszek.fsampgateway.domain.port.out;

import io.github.pauszek.fsampgateway.domain.event.DomainEvent;

public interface EventPublisherPort {

    String publish(DomainEvent event);

    String publishWithRetry(DomainEvent event, int maxRetries);
}
