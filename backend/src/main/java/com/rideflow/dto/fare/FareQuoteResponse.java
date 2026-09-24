package com.rideflow.dto.fare;

import com.rideflow.dto.common.Money;
import com.rideflow.entity.VehicleCategory;
import java.time.Instant;

/** One bookable option. {@code quoteId} is the signed token to pass to {@code POST /api/rides}. */
public record FareQuoteResponse(
        String quoteId,
        VehicleCategory vehicleCategory,
        Money estimatedFare,
        Money minimumFare,
        FareBreakdownResponse breakdown,
        Instant expiresAt) {
}
