package com.rideflow.service.ai;

import com.rideflow.ai.TripFacts;
import com.rideflow.config.AIProperties;
import com.rideflow.support.AITestSettings;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Facts of one trip, priced with the ECONOMY rate card in application.yml (base 40, 12/km, 1.5/min, booking fee
 * 10, rounded up to 1.00) and a 1.2× surge: estimated 12.8 km / 29 min → 295.00, driven 14.2 km / 38 min →
 * 331.00. Test input only; the application builds facts from the database.
 */
public final class TripFactsFixture {

    private TripFactsFixture() {
    }

    public static Map<String, Object> surgeTripValues() {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put(TripFactKeys.VEHICLE_CATEGORY, "ECONOMY");
        facts.put(TripFactKeys.PAYMENT_METHOD, "CASH");
        facts.put(TripFactKeys.MATCHING_ROUNDS, 1);
        facts.put(TripFactKeys.PICKUP_WAIT_MINUTES, new BigDecimal("6"));
        facts.put(TripFactKeys.CURRENCY, "INR");
        facts.put(TripFactKeys.ESTIMATE_TOTAL, new BigDecimal("295.00"));
        facts.put(TripFactKeys.FINAL_TOTAL, new BigDecimal("331.00"));
        facts.put(TripFactKeys.FINAL_BASE, new BigDecimal("40.00"));
        facts.put(TripFactKeys.FINAL_DISTANCE_CHARGE, new BigDecimal("170.40"));
        facts.put(TripFactKeys.FINAL_TIME_CHARGE, new BigDecimal("57.00"));
        facts.put(TripFactKeys.FINAL_BOOKING_FEE, new BigDecimal("10.00"));
        facts.put(TripFactKeys.SURGE_MULTIPLIER, new BigDecimal("1.20"));
        facts.put(TripFactKeys.MINIMUM_FARE, new BigDecimal("80.00"));
        facts.put(TripFactKeys.MINIMUM_FARE_APPLIED, false);
        facts.put(TripFactKeys.FINAL_VS_ESTIMATE_PERCENT, new BigDecimal("12"));
        facts.put(TripFactKeys.DISTANCE_ESTIMATED_KM, new BigDecimal("12.8"));
        facts.put(TripFactKeys.DISTANCE_ACTUAL_KM, new BigDecimal("14.2"));
        facts.put(TripFactKeys.DISTANCE_SOURCE, "TRACKED");
        facts.put(TripFactKeys.DISTANCE_STRAIGHT_LINE_KM, new BigDecimal("9.6"));
        facts.put(TripFactKeys.DISTANCE_VS_ESTIMATE_PERCENT, new BigDecimal("11"));
        facts.put(TripFactKeys.DETOUR_RATIO, new BigDecimal("1.48"));
        facts.put(TripFactKeys.DURATION_ESTIMATED_MINUTES, new BigDecimal("29"));
        facts.put(TripFactKeys.DURATION_ACTUAL_MINUTES, new BigDecimal("38"));
        facts.put(TripFactKeys.DURATION_VS_ESTIMATE_PERCENT, new BigDecimal("31"));
        facts.put(TripFactKeys.FINAL_PER_KM, new BigDecimal("23.31"));
        return facts;
    }

    public static TripFacts surgeTrip() {
        return withObservations(surgeTripValues());
    }

    public static TripFacts withObservations(Map<String, Object> values) {
        AIProperties properties = AITestSettings.properties(AIProperties.Provider.DISABLED, "http://unused", "unused",
                AITestSettings.TIMEOUT, AITestSettings.resilience(1, 1, 1));
        return new TripFacts(values, new TripObservationCalculator(properties).observe(values).stream()
                .map(TripObservation::text).toList());
    }
}
