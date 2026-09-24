package com.rideflow.service.ride.event;

import com.rideflow.entity.ActorType;
import com.rideflow.entity.RideStatus;
import java.time.Instant;
import java.util.UUID;

/** A committed ride status change. {@code rideVersion} lets consumers discard out-of-order deliveries. */
public record RideStatusChangedEvent(
        UUID rideId,
        RideStatus from,
        RideStatus to,
        long rideVersion,
        UUID passengerId,
        UUID driverId,
        ActorType actor,
        String reason,
        Instant occurredAt) {
}
