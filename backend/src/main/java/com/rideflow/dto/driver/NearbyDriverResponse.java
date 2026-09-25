package com.rideflow.dto.driver;

import com.rideflow.entity.VehicleCategory;
import com.rideflow.geospatial.GeoPoint;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A nearby available car. Passengers get a coarsened position and no identity ({@code driverId} and
 * {@code distanceMeters} are null); admins get exact values.
 */
public record NearbyDriverResponse(GeoPoint position, VehicleCategory category, @Nullable UUID driverId,
                                   @Nullable Double distanceMeters) {
}
