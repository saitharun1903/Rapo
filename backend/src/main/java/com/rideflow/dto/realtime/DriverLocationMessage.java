package com.rideflow.dto.realtime;

import com.rideflow.geospatial.GeoPoint;
import java.time.Instant;
import java.util.UUID;

/**
 * The assigned driver's position, pushed only to that ride's passenger on {@code /user/queue/ride-location}.
 * Clients ignore a message whose {@code recordedAt} is older than the one they are showing.
 */
public record DriverLocationMessage(UUID rideId, GeoPoint location, Integer headingDeg, Double speedMps,
                                    Instant recordedAt) {
}
