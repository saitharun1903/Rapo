package com.rideflow.service.driver.event;

import com.rideflow.geospatial.GeoPoint;
import com.rideflow.service.ride.EtaDestination;
import java.time.Instant;
import java.util.UUID;

/**
 * A new position from a driver who is engaged in a ride; routed to that ride's passenger only.
 *
 * @param destination the next stop to time, or {@code null} while the driver waits at the pickup
 */
public record DriverLocationUpdatedEvent(
        UUID driverId,
        UUID rideId,
        UUID passengerId,
        GeoPoint location,
        Integer headingDeg,
        Double speedMps,
        Instant recordedAt,
        EtaDestination destination) {
}
