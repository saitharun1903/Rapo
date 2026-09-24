package com.rideflow.service.fare;

import com.rideflow.entity.EstimateSource;
import com.rideflow.entity.FareBreakdown;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.geospatial.GeoPoint;
import java.time.Instant;
import java.util.UUID;

/**
 * The content of a signed fare quote: everything the passenger was shown, bound to them and to an
 * expiry. Booking a ride re-reads these values from the verified token instead of recalculating.
 */
public record FareQuote(
        int schemaVersion,
        UUID passengerId,
        VehicleCategory category,
        GeoPoint pickup,
        GeoPoint dropoff,
        int distanceMeters,
        int durationSeconds,
        EstimateSource estimateSource,
        FareBreakdown.Amounts fare,
        Instant expiresAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
