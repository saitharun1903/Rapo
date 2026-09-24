package com.rideflow.dto.ride;

import com.rideflow.dto.common.Money;
import com.rideflow.entity.RideStatus;
import com.rideflow.entity.VehicleCategory;
import java.time.Instant;
import java.util.UUID;

/** Trip-history row. {@code fare} is the final fare when completed, otherwise the quoted estimate. */
public record RideSummaryResponse(
        UUID id,
        RideStatus status,
        VehicleCategory vehicleCategory,
        String pickupAddress,
        String dropoffAddress,
        Money fare,
        boolean fareIsFinal,
        Instant requestedAt,
        Instant completedAt) {
}
