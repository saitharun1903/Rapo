package com.rideflow.service.ride.event;

/**
 * Port for publishing ride domain events. Must be called inside the transaction that made the change;
 * listeners only see events whose transaction committed. The current adapter is in-process
 * (Spring application events); Phase 6 replaces it with a transactional outbox relayed to Kafka.
 */
public interface RideEventPublisher {

    void publish(Object event);
}
