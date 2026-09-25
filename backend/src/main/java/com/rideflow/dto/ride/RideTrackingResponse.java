package com.rideflow.dto.ride;

import com.rideflow.entity.RideStatus;
import com.rideflow.geospatial.GeoPoint;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Tracking snapshot after page load or reconnect; live updates then arrive over WebSocket.
 *
 * @param driverLocation the driver's last known position, or {@code null} if none was ever reported
 * @param stale          {@code true} when there is no position newer than the freshness window
 * @param eta            time to the next stop (pickup, or dropoff once the trip started); {@code null} when
 *                       the position is stale or the driver is waiting at the pickup
 */
public record RideTrackingResponse(UUID rideId, RideStatus status, @Nullable DriverLocation driverLocation,
                                   boolean stale, @Nullable EtaResponse eta) {

    public record DriverLocation(GeoPoint point, @Nullable Integer headingDeg, Instant recordedAt) {
    }
}
