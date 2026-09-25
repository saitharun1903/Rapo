package com.rideflow.dto.ride;

import com.rideflow.dto.common.Money;
import com.rideflow.entity.VehicleCategory;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A pending offer as the driver sees it: enough to decide, before accepting. */
public record RideOfferResponse(
        UUID offerId,
        UUID rideId,
        int round,
        int distanceToPickupMeters,
        RideResponse.Place pickup,
        RideResponse.Place dropoff,
        VehicleCategory vehicleCategory,
        @Nullable Money estimatedFare,
        int estimatedDistanceMeters,
        int estimatedDurationSeconds,
        Instant expiresAt) {
}
