package com.rideflow.service.ride.event;

import java.util.List;
import java.util.UUID;

/** Pending offers for {@code rideId} were cancelled (another driver accepted, or the ride was cancelled). */
public record RideOffersWithdrawnEvent(UUID rideId, List<UUID> driverIds) {

    public RideOffersWithdrawnEvent {
        driverIds = List.copyOf(driverIds);
    }
}
