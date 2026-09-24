package com.rideflow.service.ride.event;

import com.rideflow.entity.ActorType;
import com.rideflow.entity.RideStatus;
import com.rideflow.service.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * A committed ride status change. {@code rideVersion} lets consumers discard out-of-order deliveries.
 *
 * @param driverId         the driver assigned after the change, if any
 * @param releasedDriverId the driver this change detached from the ride (re-dispatch), otherwise {@code null}
 */
public record RideStatusChangedEvent(
        UUID rideId,
        RideStatus from,
        RideStatus to,
        long rideVersion,
        UUID passengerId,
        UUID driverId,
        UUID releasedDriverId,
        ActorType actor,
        String reason,
        Instant occurredAt) implements DomainEvent {

    @Override
    public UUID aggregateId() {
        return rideId;
    }

    @Override
    public Long aggregateVersion() {
        return rideVersion;
    }
}
