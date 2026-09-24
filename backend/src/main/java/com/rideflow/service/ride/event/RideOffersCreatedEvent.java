package com.rideflow.service.ride.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Offers for {@code rideId} were sent to these drivers in a matching round. */
public record RideOffersCreatedEvent(UUID rideId, int round, List<UUID> driverIds, Instant expiresAt) {

    public RideOffersCreatedEvent {
        driverIds = List.copyOf(driverIds);
    }
}
