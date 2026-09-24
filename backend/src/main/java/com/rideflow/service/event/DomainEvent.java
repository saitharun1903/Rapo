package com.rideflow.service.event;

import java.util.UUID;

/**
 * Something that happened in the domain that other parts of the system react to. Each event type has one
 * Kafka topic (catalogue: docs/events.md section 1.2); its record components are the event's payload.
 */
public interface DomainEvent {

    /**
     * The entity the event is about. It is also the Kafka message key, so the events of one ride (or driver,
     * payment, user) stay in order within their topic.
     */
    UUID aggregateId();

    /** The aggregate's version after the change, where it has one (rides); consumers use it to drop stale events. */
    default Long aggregateVersion() {
        return null;
    }
}
