package com.rideflow.service.driver.event;

import com.rideflow.geospatial.GeoPoint;
import java.time.Instant;
import java.util.UUID;

/** A new position from a driver who is engaged in a ride; routed to that ride's passenger only. */
public record DriverLocationUpdatedEvent(
        UUID driverId,
        UUID rideId,
        UUID passengerId,
        GeoPoint location,
        Integer headingDeg,
        Double speedMps,
        Instant recordedAt) {
}
