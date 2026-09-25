package com.rideflow.dto.admin;

import com.rideflow.dto.common.Money;
import com.rideflow.entity.RideStatus;
import com.rideflow.entity.VehicleCategory;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A ride in the admin search. {@code fare} is the final fare when completed, otherwise the estimate. */
public record AdminRideSummaryResponse(
        UUID id,
        RideStatus status,
        VehicleCategory vehicleCategory,
        UUID passengerId,
        @Nullable UUID driverId,
        String pickupAddress,
        String dropoffAddress,
        @Nullable Money fare,
        boolean fareIsFinal,
        Instant requestedAt,
        @Nullable Instant completedAt) {
}
