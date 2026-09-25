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
import static com.rideflow.service.ai.TripFactKeys.FINAL_BASE;
import static com.rideflow.service.ai.TripFactKeys.FINAL_BOOKING_FEE;
import static com.rideflow.service.ai.TripFactKeys.FINAL_DISTANCE_CHARGE;
import static com.rideflow.service.ai.TripFactKeys.FINAL_PER_KM;
import static com.rideflow.service.ai.TripFactKeys.FINAL_TIME_CHARGE;
import static com.rideflow.service.ai.TripFactKeys.FINAL_TOTAL;
import static com.rideflow.service.ai.TripFactKeys.FINAL_VS_ESTIMATE_PERCENT;
import static com.rideflow.service.ai.TripFactKeys.HISTORY_AVG_FARE;
import static com.rideflow.service.ai.TripFactKeys.HISTORY_AVG_FARE_PER_KM;
import static com.rideflow.service.ai.TripFactKeys.HISTORY_AVG_SURGE;
import static com.rideflow.service.ai.TripFactKeys.HISTORY_FARE_PER_KM_VS_AVG_PERCENT;
import static com.rideflow.service.ai.TripFactKeys.HISTORY_TRIPS;
import static com.rideflow.service.ai.TripFactKeys.MATCHING_ROUNDS;
import static com.rideflow.service.ai.TripFactKeys.MINIMUM_FARE;
import static com.rideflow.service.ai.TripFactKeys.MINIMUM_FARE_APPLIED;
import static com.rideflow.service.ai.TripFactKeys.PAYMENT_METHOD;
import static com.rideflow.service.ai.TripFactKeys.PICKUP_WAIT_MINUTES;
import static com.rideflow.service.ai.TripFactKeys.SURGE_MULTIPLIER;
import static com.rideflow.service.ai.TripFactKeys.VEHICLE_CATEGORY;

import com.rideflow.ai.TripFacts;
import com.rideflow.config.AIProperties;
import com.rideflow.entity.FareBreakdown;
import com.rideflow.entity.FareKind;
import com.rideflow.entity.Ride;
import com.rideflow.geospatial.GeoMath;
import com.rideflow.repository.FareBreakdownRepository;
import com.rideflow.repository.TripHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds a completed trip's {@link TripFacts} from the database only: the ride, its estimated and final fare
 * snapshots, and the passenger's own recent history. Values are rounded to the precision a person would quote
 * (money to 0.01, km to 0.1, minutes and percentages to whole numbers), so the model can repeat them exactly.
 */
@Component
public class TripFactsAssembler {

    private static final BigDecimal METERS_PER_KM = BigDecimal.valueOf(1000);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 2;
    private static final int KM_SCALE = 1;
    private static final int RATIO_SCALE = 2;
    private static final int DIVISION_SCALE = 6;

    private final FareBreakdownRepository fareBreakdowns;
    private final TripHistoryRepository history;
    private final TripObservationCalculator observations;
    private final AIProperties.Observations settings;

    public TripFactsAssembler(FareBreakdownRepository fareBreakdowns, TripHistoryRepository history,
                              TripObservationCalculator observations, AIProperties properties) {
        this.fareBreakdowns = fareBreakdowns;
        this.history = history;
        this.observations = observations;
        this.settings = properties.observations();
    }

    /** A trip's facts and the observations computed from them. */
    public record AssembledTrip(TripFacts facts, List<TripObservation> observations) {
    }

    /** @param ride a COMPLETED ride */
    public AssembledTrip assemble(Ride ride) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put(VEHICLE_CATEGORY, ride.getVehicleCategory().name());
        facts.put(PAYMENT_METHOD, ride.getPaymentMethod().name());
        facts.put(MATCHING_ROUNDS, ride.getMatchingRound());
        if (ride.getAcceptedAt() != null && ride.getArrivedAt() != null) {
            facts.put(PICKUP_WAIT_MINUTES, minutes(Duration.between(ride.getAcceptedAt(), ride.getArrivedAt()).toSeconds()));
        }

        FareBreakdown estimate = fare(ride, FareKind.ESTIMATE);
        FareBreakdown fin = fare(ride, FareKind.FINAL);
        BigDecimal finalTotal = money(fin.getTotal());
        FareBreakdown.Amounts amounts = fin.amounts();
        facts.put(CURRENCY, ride.getCurrency());
        facts.put(ESTIMATE_TOTAL, money(estimate.getTotal()));
        facts.put(FINAL_TOTAL, finalTotal);
        facts.put(FINAL_BASE, money(amounts.baseFare()));
        facts.put(FINAL_DISTANCE_CHARGE, money(amounts.distanceCharge()));
        facts.put(FINAL_TIME_CHARGE, money(amounts.timeCharge()));
        facts.put(FINAL_BOOKING_FEE, money(amounts.bookingFee()));
        facts.put(SURGE_MULTIPLIER, amounts.surgeMultiplier().setScale(RATIO_SCALE, RoundingMode.HALF_UP));
        facts.put(MINIMUM_FARE, money(amounts.minimumFare()));
        facts.put(MINIMUM_FARE_APPLIED, amounts.minimumFareApplied());
        putPercent(facts, FINAL_VS_ESTIMATE_PERCENT, finalTotal, money(estimate.getTotal()));

        BigDecimal estimatedKm = km(ride.getEstimatedDistanceMeters());
        BigDecimal actualKm = km(ride.getActualDistanceMeters());
        BigDecimal straightKm = km((int) Math.round(GeoMath.haversineMeters(ride.getPickup(), ride.getDropoff())));
        facts.put(DISTANCE_ESTIMATED_KM, estimatedKm);
        facts.put(DISTANCE_ACTUAL_KM, actualKm);
        facts.put(DISTANCE_SOURCE, ride.getDistanceSource().name());
        facts.put(DISTANCE_STRAIGHT_LINE_KM, straightKm);
        putPercent(facts, DISTANCE_VS_ESTIMATE_PERCENT, actualKm, estimatedKm);
        if (straightKm.signum() > 0) {
            facts.put(DETOUR_RATIO, actualKm.divide(straightKm, RATIO_SCALE, RoundingMode.HALF_UP));
        }

        BigDecimal estimatedMinutes = minutes(ride.getEstimatedDurationSeconds());
        BigDecimal actualMinutes = minutes(ride.getActualDurationSeconds());
        facts.put(DURATION_ESTIMATED_MINUTES, estimatedMinutes);
        facts.put(DURATION_ACTUAL_MINUTES, actualMinutes);
        putPercent(facts, DURATION_VS_ESTIMATE_PERCENT, actualMinutes, estimatedMinutes);

        BigDecimal perKm = actualKm.signum() > 0 ? finalTotal.divide(actualKm, MONEY_SCALE, RoundingMode.HALF_UP) : null;
        if (perKm != null) {
            facts.put(FINAL_PER_KM, perKm);
        }
        putHistory(facts, ride, perKm);

        List<TripObservation> computed = observations.observe(facts);
        return new AssembledTrip(new TripFacts(facts, computed.stream().map(TripObservation::text).toList()), computed);
    }

    private void putHistory(Map<String, Object> facts, Ride ride, BigDecimal perKm) {
        TripHistoryRepository.History past = history.averages(ride.getPassengerId(), ride.getId(), ride.getCurrency(),
                ride.getCompletedAt(), settings.historyMaxTrips());
        if (past.trips() < settings.historyMinTrips()) {
            return;
        }
        BigDecimal avgPerKm = money(past.avgFarePerKm());
        facts.put(HISTORY_TRIPS, past.trips());
        facts.put(HISTORY_AVG_FARE, money(past.avgFare()));
        facts.put(HISTORY_AVG_FARE_PER_KM, avgPerKm);
        facts.put(HISTORY_AVG_SURGE, past.avgSurge().setScale(RATIO_SCALE, RoundingMode.HALF_UP));
        if (perKm != null) {
            putPercent(facts, HISTORY_FARE_PER_KM_VS_AVG_PERCENT, perKm, avgPerKm);
        }
    }

    private FareBreakdown fare(Ride ride, FareKind kind) {
        return fareBreakdowns.findByRideIdAndKind(ride.getId(), kind).orElseThrow(() ->
                new IllegalStateException("Completed ride " + ride.getId() + " has no " + kind + " fare"));
    }

    /** Signed whole percentage by which {@code actual} differs from {@code expected}; omitted when expected is 0. */
    private static void putPercent(Map<String, Object> facts, String key, BigDecimal actual, BigDecimal expected) {
        if (expected.signum() != 0) {
            facts.put(key, actual.subtract(expected).multiply(HUNDRED)
                    .divide(expected, DIVISION_SCALE, RoundingMode.HALF_UP).setScale(0, RoundingMode.HALF_UP));
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal km(int meters) {
        return BigDecimal.valueOf(meters).divide(METERS_PER_KM, KM_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal minutes(long seconds) {
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(Duration.ofMinutes(1).toSeconds()), 0,
                RoundingMode.HALF_UP);
    }
}
