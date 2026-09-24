package com.rideflow.service.ride.event;

import com.rideflow.service.event.DomainEvent;
import java.util.UUID;

/**
 * The ride needs its next matching round now rather than when the sweeper next looks: its last open offer
 * was rejected, or its driver backed out. (A new ride is matched from {@code ride.requested}.)
 */
public record MatchingRoundRequestedEvent(UUID rideId) implements DomainEvent {

    @Override
    public UUID aggregateId() {
        return rideId;
    }
}
