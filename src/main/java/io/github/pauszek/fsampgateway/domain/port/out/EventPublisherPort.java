package io.github.pauszek.fsampgateway.domain.port.out;

import io.github.pauszek.fsampgateway.domain.event.DomainEvent;

/**
 * Secondary Port (Driven) - Event Publisher.
 * 
 * This is the interface that the domain uses to publish events.
 * Implementation is in the adapter layer (e.g., SNS adapter).
 */
public interface EventPublisherPort {

    /**
     * Publish a domain event.
     *
     * @param event the domain event to publish
     * @return message ID from the messaging system
     */
    String publish(DomainEvent event);

    /**
     * Publish an event with retry.
     *
     * @param event      the domain event to publish
     * @param maxRetries maximum retry attempts
     * @return message ID from the messaging system
     */
    String publishWithRetry(DomainEvent event, int maxRetries);
}
