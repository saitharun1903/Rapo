package com.rideflow.service.ride.event;

import com.rideflow.service.event.DomainEvent;
import java.util.List;
import java.util.UUID;

/** Pending offers for {@code rideId} were cancelled (another driver accepted, or the ride was cancelled). */
public record RideOffersWithdrawnEvent(UUID rideId, List<UUID> driverIds) implements DomainEvent {

    public RideOffersWithdrawnEvent {
        driverIds = List.copyOf(driverIds);
    }

    @Override
    public UUID aggregateId() {
        return rideId;
    }
}
