package com.rideflow.service.ai;

import static com.rideflow.service.ai.TripFactKeys.CURRENCY;
import static com.rideflow.service.ai.TripFactKeys.DETOUR_RATIO;
import static com.rideflow.service.ai.TripFactKeys.DISTANCE_ACTUAL_KM;
import static com.rideflow.service.ai.TripFactKeys.DISTANCE_ESTIMATED_KM;
import static com.rideflow.service.ai.TripFactKeys.DISTANCE_SOURCE;
import static com.rideflow.service.ai.TripFactKeys.DISTANCE_STRAIGHT_LINE_KM;
import static com.rideflow.service.ai.TripFactKeys.DISTANCE_VS_ESTIMATE_PERCENT;
import static com.rideflow.service.ai.TripFactKeys.DURATION_ACTUAL_MINUTES;
import static com.rideflow.service.ai.TripFactKeys.DURATION_ESTIMATED_MINUTES;
import static com.rideflow.service.ai.TripFactKeys.DURATION_VS_ESTIMATE_PERCENT;
import static com.rideflow.service.ai.TripFactKeys.ESTIMATE_TOTAL;
import static com.rideflow.service.ai.TripFactKeys.FINAL_PER_KM;
import static com.rideflow.service.ai.TripFactKeys.FINAL_TOTAL;
import static com.rideflow.service.ai.TripFactKeys.FINAL_VS_ESTIMATE_PERCENT;
import static com.rideflow.service.ai.TripFactKeys.HISTORY_AVG_FARE_PER_KM;
import static com.rideflow.service.ai.TripFactKeys.HISTORY_FARE_PER_KM_VS_AVG_PERCENT;
import static com.rideflow.service.ai.TripFactKeys.HISTORY_TRIPS;
import static com.rideflow.service.ai.TripFactKeys.MINIMUM_FARE;
import static com.rideflow.service.ai.TripFactKeys.MINIMUM_FARE_APPLIED;
import static com.rideflow.service.ai.TripFactKeys.SURGE_MULTIPLIER;

import com.rideflow.config.AIProperties;
import com.rideflow.entity.DistanceSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Deterministic observations from a trip's facts. Every number in a text is taken verbatim from a fact, so the
 * texts are as trustworthy as the facts; the AI analysis adds explanation on top of them, never replaces them.
 * Pure: facts in, observations out.
 */
@Component
public class TripObservationCalculator {

    private final AIProperties.Observations thresholds;

    public TripObservationCalculator(AIProperties properties) {
        this.thresholds = properties.observations();
    }

    public List<TripObservation> observe(Map<String, Object> facts) {
        List<TripObservation> observations = new ArrayList<>();
        String currency = (String) facts.get(CURRENCY);

        BigDecimal surge = decimal(facts, SURGE_MULTIPLIER);
        if (surge != null && surge.compareTo(BigDecimal.ONE) > 0) {
            // The multiplier is locked at booking, so it cannot explain a difference between estimate and final fare.
            observations.add(new TripObservation("surge.applied", "A demand multiplier of " + plain(surge)
                    + "× was locked in at booking time; it applies equally to the estimate and the final fare."));
        }
        if (Boolean.TRUE.equals(facts.get(MINIMUM_FARE_APPLIED))) {
            observations.add(new TripObservation("fare.minimumApplied", "The minimum fare of "
                    + money(decimal(facts, MINIMUM_FARE)) + " " + currency + " applied because the metered fare was lower."));
        }
        BigDecimal farePercent = decimal(facts, FINAL_VS_ESTIMATE_PERCENT);
        if (reaches(farePercent, thresholds.fareDeltaPercent())) {
            observations.add(new TripObservation("fare.vsEstimate", "The final fare (" + money(decimal(facts, FINAL_TOTAL))
                    + " " + currency + ") was " + plain(farePercent.abs()) + "% " + (farePercent.signum() > 0 ? "higher" : "lower")
                    + " than the estimate (" + money(decimal(facts, ESTIMATE_TOTAL)) + " " + currency + ")."));
        }
        boolean tracked = DistanceSource.TRACKED.name().equals(facts.get(DISTANCE_SOURCE));
        BigDecimal distancePercent = decimal(facts, DISTANCE_VS_ESTIMATE_PERCENT);
        if (tracked && reaches(distancePercent, thresholds.distanceDeltaPercent())) {
            observations.add(new TripObservation("distance.vsEstimate", "The trip covered "
                    + plain(decimal(facts, DISTANCE_ACTUAL_KM)) + " km, " + plain(distancePercent.abs()) + "% "
                    + (distancePercent.signum() > 0 ? "more" : "less") + " than the estimated "
                    + plain(decimal(facts, DISTANCE_ESTIMATED_KM)) + " km."));
        }
        if (!tracked && facts.containsKey(DISTANCE_SOURCE)) {
            observations.add(new TripObservation("distance.estimatedUsed",
                    "Too little GPS data was recorded during the trip, so the fare used the estimated distance."));
        }
        BigDecimal durationPercent = decimal(facts, DURATION_VS_ESTIMATE_PERCENT);
        if (reaches(durationPercent, thresholds.durationDeltaPercent())) {
            observations.add(new TripObservation("duration.vsEstimate", "The trip took "
                    + plain(decimal(facts, DURATION_ACTUAL_MINUTES)) + " minutes, " + plain(durationPercent.abs()) + "% "
                    + (durationPercent.signum() > 0 ? "longer" : "shorter") + " than the estimated "
                    + plain(decimal(facts, DURATION_ESTIMATED_MINUTES)) + " minutes."));
        }
        BigDecimal detour = decimal(facts, DETOUR_RATIO);
        if (tracked && detour != null && detour.compareTo(thresholds.detourRatio()) >= 0) {
            observations.add(new TripObservation("route.detour", "The distance driven was " + plain(detour)
                    + "× the straight-line distance between pickup and drop-off ("
                    + plain(decimal(facts, DISTANCE_STRAIGHT_LINE_KM)) + " km)."));
        }
        BigDecimal historyPercent = decimal(facts, HISTORY_FARE_PER_KM_VS_AVG_PERCENT);
        if (historyPercent != null && historyPercent.signum() > 0
                && historyPercent.compareTo(thresholds.historyFarePerKmDeltaPercent()) >= 0) {
            observations.add(new TripObservation("history.farePerKmAboveAverage", "The fare per km ("
                    + money(decimal(facts, FINAL_PER_KM)) + " " + currency + ") was " + plain(historyPercent)
                    + "% above your average of " + money(decimal(facts, HISTORY_AVG_FARE_PER_KM)) + " " + currency
                    + " over your last " + plain(decimal(facts, HISTORY_TRIPS)) + " trips."));
        }
        return observations;
    }

    private static boolean reaches(BigDecimal percent, BigDecimal threshold) {
        return percent != null && percent.abs().compareTo(threshold) >= 0;
    }

    private static BigDecimal decimal(Map<String, Object> facts, String key) {
        Object value = facts.get(key);
        return value == null ? null : new BigDecimal(value.toString());
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Without trailing zeros: "1.2", "13". */
    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
