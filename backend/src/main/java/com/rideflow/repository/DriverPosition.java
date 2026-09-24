package com.rideflow.repository;

import com.rideflow.geospatial.GeoPoint;
import java.time.Instant;

/** A driver's last persisted position; {@code updatedAt} is server time and drives freshness checks. */
public record DriverPosition(GeoPoint point, Integer headingDeg, Instant recordedAt, Instant updatedAt) {
}
