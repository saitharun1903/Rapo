package com.rideflow.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideflow.config.AIProperties;
import com.rideflow.support.AITestSettings;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TripObservationCalculatorTest {

    private final TripObservationCalculator calculator = new TripObservationCalculator(AITestSettings.properties(
            AIProperties.Provider.DISABLED, "http://unused", "unused", AITestSettings.TIMEOUT,
            AITestSettings.resilience(1, 1, 1)));

    private List<String> keys(Map<String, Object> facts) {
        return calculator.observe(facts).stream().map(TripObservation::key).toList();
    }

    @Test
    void notableDifferencesAreStatedWithTheFactsNumbers() {
        List<TripObservation> observations = calculator.observe(TripFactsFixture.surgeTripValues());

        assertThat(observations).extracting(TripObservation::key)
                .containsExactly("surge.applied", "fare.vsEstimate", "distance.vsEstimate", "duration.vsEstimate");
        assertThat(observations).extracting(TripObservation::text).containsExactly(
                "A demand multiplier of 1.2× was locked in at booking time; it applies equally to the estimate and "
                        + "the final fare.",
                "The final fare (331.00 INR) was 12% higher than the estimate (295.00 INR).",
                "The trip covered 14.2 km, 11% more than the estimated 12.8 km.",
                "The trip took 38 minutes, 31% longer than the estimated 29 minutes.");
    }

    @Test
    void aShortTripsDurationIsNotRemarkedOnForAMinutesDifference() {
        Map<String, Object> facts = TripFactsFixture.surgeTripValues();
        facts.put(TripFactKeys.DURATION_ACTUAL_MINUTES, new BigDecimal("0"));
        facts.put(TripFactKeys.DURATION_ESTIMATED_MINUTES, new BigDecimal("1"));
        facts.put(TripFactKeys.DURATION_VS_ESTIMATE_PERCENT, new BigDecimal("-100"));

        assertThat(keys(facts)).doesNotContain("duration.vsEstimate");
    }

    @Test
    void durationsAreWordedInTheSingularAndUnderAMinute() {
        Map<String, Object> facts = TripFactsFixture.surgeTripValues();
        facts.put(TripFactKeys.DURATION_ACTUAL_MINUTES, new BigDecimal("0"));
        facts.put(TripFactKeys.DURATION_ESTIMATED_MINUTES, new BigDecimal("3"));
        facts.put(TripFactKeys.DURATION_VS_ESTIMATE_PERCENT, new BigDecimal("-100"));
        assertThat(calculator.observe(facts)).extracting(TripObservation::text)
                .contains("The trip took less than a minute, 100% shorter than the estimated 3 minutes.");

        facts.put(TripFactKeys.DURATION_ACTUAL_MINUTES, new BigDecimal("1"));
        facts.put(TripFactKeys.DURATION_ESTIMATED_MINUTES, new BigDecimal("4"));
        facts.put(TripFactKeys.DURATION_VS_ESTIMATE_PERCENT, new BigDecimal("-75"));
        assertThat(calculator.observe(facts)).extracting(TripObservation::text)
                .contains("The trip took 1 minute, 75% shorter than the estimated 4 minutes.");
    }

    @Test
    void smallDifferencesAndAnOrdinaryRouteAreNotMentioned() {
        Map<String, Object> facts = TripFactsFixture.surgeTripValues();
        facts.put(TripFactKeys.SURGE_MULTIPLIER, new BigDecimal("1.00"));
        facts.put(TripFactKeys.FINAL_VS_ESTIMATE_PERCENT, new BigDecimal("4"));
        facts.put(TripFactKeys.DISTANCE_VS_ESTIMATE_PERCENT, new BigDecimal("-9"));
        facts.put(TripFactKeys.DURATION_VS_ESTIMATE_PERCENT, new BigDecimal("19"));

        assertThat(keys(facts)).isEmpty();
    }

    @Test
    void anUntrackedTripSaysTheEstimateWasUsedAndMakesNoRouteClaims() {
        Map<String, Object> facts = TripFactsFixture.surgeTripValues();
        facts.put(TripFactKeys.DISTANCE_SOURCE, "ESTIMATED");
        facts.put(TripFactKeys.DETOUR_RATIO, new BigDecimal("2.40"));

        assertThat(keys(facts)).contains("distance.estimatedUsed").doesNotContain("distance.vsEstimate", "route.detour");
    }

    @Test
    void detoursMinimumFaresAndPricierThanUsualTripsAreExplained() {
        Map<String, Object> facts = TripFactsFixture.surgeTripValues();
        facts.put(TripFactKeys.DETOUR_RATIO, new BigDecimal("1.62"));
        facts.put(TripFactKeys.MINIMUM_FARE_APPLIED, true);
        facts.put(TripFactKeys.HISTORY_TRIPS, 5);
        facts.put(TripFactKeys.HISTORY_AVG_FARE_PER_KM, new BigDecimal("18.40"));
        facts.put(TripFactKeys.HISTORY_FARE_PER_KM_VS_AVG_PERCENT, new BigDecimal("27"));

        List<TripObservation> observations = calculator.observe(facts);

        assertThat(observations).extracting(TripObservation::key)
                .contains("route.detour", "fare.minimumApplied", "history.farePerKmAboveAverage");
        assertThat(observations).extracting(TripObservation::text)
                .contains("The distance driven was 1.62× the straight-line distance between pickup and drop-off (9.6 km).",
                        "The fare per km (23.31 INR) was 27% above your average of 18.40 INR over your last 5 trips.");
    }
}
