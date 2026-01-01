package io.github.pauszek.fsampgateway.domain.event;

import java.time.Instant;

/**
 * Base interface for all domain events.
 * 
 * Domain events represent something that happened in the domain
 * that domain experts care about.
 */
public interface DomainEvent {
    
    /**
     * Get the event type identifier.
     */
    String getEventType();
    
    /**
     * Get when this event occurred.
     */
    Instant getOccurredAt();
}
