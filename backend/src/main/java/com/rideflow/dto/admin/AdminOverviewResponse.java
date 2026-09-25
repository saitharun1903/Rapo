package com.rideflow.dto.admin;

import com.rideflow.dto.common.Money;
import com.rideflow.entity.DriverAvailability;
import com.rideflow.entity.RideStatus;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Rides requested in {@code [from, to)}, by current status, and the money from rides completed in the window.
 *
 * @param completionRate       completed / (completed + cancelled + expired) among those rides; {@code null}
 *                             when none has finished
 * @param cancellationRate     cancelled / the same denominator
 * @param medianSecondsToMatch median time from request to a driver accepting; {@code null} when none was
 * @param verifiedDrivers      verified drivers by current availability (all statuses present, zero included)
 */
public record AdminOverviewResponse(
        Instant from,
        Instant to,
        long ridesRequested,
        Map<RideStatus, Long> ridesByStatus,
        @Nullable Double completionRate,
        @Nullable Double cancellationRate,
        @Nullable Double medianSecondsToMatch,
        Money grossFares,
        Money platformFees,
        Map<DriverAvailability, Long> verifiedDrivers) {
}
