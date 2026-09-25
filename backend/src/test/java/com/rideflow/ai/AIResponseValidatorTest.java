package com.rideflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.config.AIProperties;
import com.rideflow.service.ai.TripFactsFixture;
import com.rideflow.support.AITestSettings;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AIResponseValidatorTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    private final AIResponseValidator validator = new AIResponseValidator(json, validation.getValidator(),
            AITestSettings.properties(AIProperties.Provider.DISABLED, "http://unused", "unused", AITestSettings.TIMEOUT,
                    AITestSettings.resilience(1, 1, 1)));
    private final TripFacts facts = TripFactsFixture.surgeTrip();

    @AfterEach
    void close() {
        validation.close();
    }

    private Map<String, Object> insights(String summary) {
        Map<String, Object> insights = new LinkedHashMap<>();
        insights.put("summary", summary);
        insights.put("fareExplanation", "Distance and time were above the estimate.");
        insights.put("observations", List.of(Map.of("type", "FARE", "text", "The fare rose.")));
        insights.put("recommendations", List.of());
        insights.put("comparison", null);
        insights.put("factKeysUsed", List.of("fare.final.total"));
        return insights;
    }

    private String text(Map<String, Object> value) {
        return json.writeValueAsString(value);
    }

    private static void assertInvalid(ThrowingCallable call, String problem) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AIException.class, ex -> {
            assertThat(ex.code()).isEqualTo(AIFailureCode.INVALID_RESPONSE);
            assertThat(ex.getMessage()).contains(problem);
        });
    }

    @Test
    void numbersFromTheFactsAreAcceptedAsWrittenOrRounded() {
        String summary = "You paid 331.00 INR (about 331), 12% above 295 INR, for 14.2 km (roughly 14 km) in 38 minutes"
                + " at a 1.2× multiplier.";

        assertThat(validator.insights(text(insights(summary)), facts).summary()).isEqualTo(summary);
    }

    @Test
    void numbersFromTheComputedObservationsCount() {
        // 31 appears only in the observation "... 31% longer than the estimated 29 minutes."
        assertThat(validator.insights(text(insights("The trip took 31% longer.")), facts)).isNotNull();
    }

    @Test
    void anInventedNumberIsRejected() {
        assertInvalid(() -> validator.insights(text(insights("Heavy traffic added 45 INR.")), facts),
                "the number 45 is not one of the supplied facts");
    }

    @Test
    void aNumberOutsideTheToleranceIsRejected() {
        // 1% of 331.00 is 3.31: 335 is too far off to be a rounding of the fare.
        assertInvalid(() -> validator.insights(text(insights("You paid 335 INR.")), facts), "335");
    }

    @Test
    void citedKeysMustBeSuppliedFacts() {
        Map<String, Object> insights = insights("Your fare followed the rate card.");
        insights.put("factKeysUsed", List.of("fare.final.total", "traffic.level"));

        assertInvalid(() -> validator.insights(text(insights), facts), "\"traffic.level\"");
    }

    @Test
    void aComparisonNeedsHistoryFacts() {
        Map<String, Object> insights = insights("Your fare followed the rate card.");
        insights.put("comparison", "Higher than usual.");

        assertInvalid(() -> validator.insights(text(insights), facts), "comparison must be null");

        Map<String, Object> withHistory = new LinkedHashMap<>(TripFactsFixture.surgeTripValues());
        withHistory.put("history.tripCount", 4);
        withHistory.put("history.avgFare", new BigDecimal("250.00"));
        assertThat(validator.insights(text(insights), TripFactsFixture.withObservations(withHistory)).comparison())
                .isEqualTo("Higher than usual.");
    }

    @Test
    void limitsAreEnforcedClientSide() {
        Map<String, Object> insights = insights("x".repeat(601));
        insights.put("recommendations", List.of("a", "b", "c", "d"));

        assertInvalid(() -> validator.insights(text(insights), facts), "recommendations size must be between 0 and 3");
        assertInvalid(() -> validator.insights(text(insights), facts), "summary size must be between 0 and 600");
    }

    @Test
    void surroundingProseIsToleratedButUnknownFieldsAreNot() {
        String fenced = "```json\n" + text(insights("Your fare followed the rate card.")) + "\n```";
        assertThat(validator.insights(fenced, facts)).isNotNull();

        Map<String, Object> extra = insights("Your fare followed the rate card.");
        extra.put("confidence", "high");
        assertInvalid(() -> validator.insights(text(extra), facts), "not valid JSON of the required shape");
        assertInvalid(() -> validator.insights("I cannot help with that.", facts), "contains no JSON object");
    }

    @Test
    void answersAreGroundedToo() {
        String answer = text(Map.of("answerable", true, "answer", "Because of a 50% surge.",
                "factKeysUsed", List.of("fare.final.surgeMultiplier")));

        assertInvalid(() -> validator.answer(answer, facts), "the number 50");
    }
}
