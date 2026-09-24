package com.rideflow.dto.ride;

import com.rideflow.entity.EstimateSource;
import com.rideflow.entity.RideStatus;
import com.rideflow.geospatial.GeoPoint;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracking snapshot after page load or reconnect; live updates then arrive over WebSocket.
 *
 * @param driverLocation the driver's last known position, or {@code null} if none was ever reported
 * @param stale          {@code true} when there is no position newer than the freshness window
 * @param eta            time to the next stop (pickup, or dropoff once the trip started); {@code null} when
 *                       the position is stale or the driver is waiting at the pickup
 */
public record RideTrackingResponse(UUID rideId, RideStatus status, DriverLocation driverLocation, boolean stale,
                                   Eta eta) {

    public record DriverLocation(GeoPoint point, Integer headingDeg, Instant recordedAt) {
    }

    public enum Target {
        PICKUP,
        DROPOFF
    }

    /** {@code source} says whether the road router or the straight-line fallback produced the numbers. */
    public record Eta(Target target, int seconds, int distanceMeters, EstimateSource source) {
    }
}
