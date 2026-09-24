package com.rideflow.service.driver.event;

import com.rideflow.entity.RideStatus;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.service.event.DomainEvent;
import com.rideflow.service.ride.EtaDestination;
import java.time.Instant;
import java.util.UUID;

/**
 * An accepted position report from an online driver. Persisted in batches, and pushed to the passenger of
 * the driver's ride when there is one.
 *
 * @param rideId      the ride the driver is engaged in, or {@code null}
 * @param passengerId that ride's passenger (the only user who may see this position), or {@code null}
 * @param rideStatus  that ride's status when the report was received, or {@code null}
 * @param receivedAt  server time of the report; freshness checks use it, not the device clock
 * @param destination the next stop to time, or {@code null} (no ride, or waiting at the pickup)
 */
public record DriverLocationUpdatedEvent(
        UUID driverId,
        UUID rideId,
        UUID passengerId,
        RideStatus rideStatus,
        GeoPoint location,
        Integer headingDeg,
        Double speedMps,
        Double accuracyMeters,
        Instant recordedAt,
        Instant receivedAt,
        EtaDestination destination) implements DomainEvent {

    @Override
    public UUID aggregateId() {
        return driverId;
    }
}
