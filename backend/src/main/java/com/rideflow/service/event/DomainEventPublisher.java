package com.rideflow.service.event;

/**
 * Port for publishing domain events. Must be called inside the transaction that made the change: the adapter
 * stores the event in the transactional outbox (docs/architecture.md D5), so it reaches Kafka if and only if
 * that transaction commits. High-volume, loss-tolerant location reports do not use this port; see
 * {@code com.rideflow.service.driver.LocationStream}.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
