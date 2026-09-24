package com.rideflow.repository;

import com.rideflow.entity.VehicleCategory;
import com.rideflow.geospatial.GeoPoint;
import java.util.UUID;

/** A candidate driver returned by the PostGIS proximity query, nearest first. */
public record NearbyDriver(
        UUID driverId, UUID vehicleId, VehicleCategory category, GeoPoint position, double distanceMeters) {
}
