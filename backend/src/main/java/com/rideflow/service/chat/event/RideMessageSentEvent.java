package com.rideflow.service.chat.event;

import com.rideflow.service.event.DomainEvent;
import java.util.UUID;

/**
 * A chat message was stored. It carries ids only: the realtime bridge loads the text when it pushes, so message
 * bodies never sit in Kafka.
 */
public record RideMessageSentEvent(UUID messageId, UUID rideId, UUID passengerId, UUID driverId) implements DomainEvent {

    @Override
    public UUID aggregateId() {
        return rideId;
    }
}
