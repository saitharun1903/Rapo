package com.rideflow.service.event;

/**
 * Port for publishing domain events (ride, offer, driver location and presence). Must be called inside the transaction that made the change;
 * listeners only see events whose transaction committed. The current adapter is in-process
 * (Spring application events); Phase 6 replaces it with a transactional outbox relayed to Kafka.
 */
public interface DomainEventPublisher {

    void publish(Object event);
}
