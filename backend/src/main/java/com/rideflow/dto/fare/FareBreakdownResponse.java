package com.rideflow.dto.fare;

/** Line items of a fare calculation; amounts are decimal strings in {@code currency}. */
public record FareBreakdownResponse(
        String baseFare,
        String distanceCharge,
        String timeCharge,
        String subtotal,
        String surgeMultiplier,
        String bookingFee,
        String minimumFare,
        boolean minimumFareApplied,
        String total,
        String currency,
        int distanceMeters,
        int durationSeconds,
        String pricingVersion) {
}
