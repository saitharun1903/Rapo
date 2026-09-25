package com.rideflow.service.ai;

/**
 * The fact keys a trip is described with. Deliberately absent: names, contact details, addresses and
 * coordinates. Nothing in the facts identifies a person or a place, so they can be sent to an external
 * provider.
 */
final class TripFactKeys {

    static final String VEHICLE_CATEGORY = "ride.vehicleCategory";
    static final String PAYMENT_METHOD = "ride.paymentMethod";
    static final String MATCHING_ROUNDS = "ride.matchingRounds";
    static final String PICKUP_WAIT_MINUTES = "ride.pickupWaitMinutes";

    static final String CURRENCY = "fare.currency";
    static final String ESTIMATE_TOTAL = "fare.estimate.total";
    static final String FINAL_TOTAL = "fare.final.total";
    static final String FINAL_BASE = "fare.final.baseFare";
    static final String FINAL_DISTANCE_CHARGE = "fare.final.distanceCharge";
    static final String FINAL_TIME_CHARGE = "fare.final.timeCharge";
    static final String FINAL_BOOKING_FEE = "fare.final.bookingFee";
    static final String SURGE_MULTIPLIER = "fare.final.surgeMultiplier";
    static final String MINIMUM_FARE = "fare.final.minimumFare";
    static final String MINIMUM_FARE_APPLIED = "fare.final.minimumFareApplied";
    static final String FINAL_PER_KM = "fare.final.perKm";
    static final String FINAL_VS_ESTIMATE_PERCENT = "fare.finalVsEstimatePercent";

    static final String DISTANCE_ESTIMATED_KM = "distance.estimatedKm";
    static final String DISTANCE_ACTUAL_KM = "distance.actualKm";
    static final String DISTANCE_SOURCE = "distance.source";
    static final String DISTANCE_VS_ESTIMATE_PERCENT = "distance.actualVsEstimatePercent";
    static final String DISTANCE_STRAIGHT_LINE_KM = "distance.straightLineKm";
    static final String DETOUR_RATIO = "route.detourRatio";

    static final String DURATION_ESTIMATED_MINUTES = "duration.estimatedMinutes";
    static final String DURATION_ACTUAL_MINUTES = "duration.actualMinutes";
    static final String DURATION_VS_ESTIMATE_PERCENT = "duration.actualVsEstimatePercent";

    static final String HISTORY_TRIPS = "history.tripCount";
    static final String HISTORY_AVG_FARE = "history.avgFare";
    static final String HISTORY_AVG_FARE_PER_KM = "history.avgFarePerKm";
    static final String HISTORY_AVG_SURGE = "history.avgSurgeMultiplier";
    static final String HISTORY_FARE_PER_KM_VS_AVG_PERCENT = "history.farePerKmVsAveragePercent";

    private TripFactKeys() {
    }
}
