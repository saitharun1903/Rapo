package com.rideflow.service.fare;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideflow.config.SurgeProperties;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SurgeCalculatorTest {

    private final SurgeCalculator calculator = new SurgeCalculator(new SurgeProperties(true, 2000, Duration.ofMinutes(10),
            new BigDecimal("0.5"), new BigDecimal("1.0"), new BigDecimal("2.5"), new BigDecimal("0.1")));

    @ParameterizedTest(name = "demand {0}, supply {1} -> {2}")
    @CsvSource({
        "0, 0, 1.00",     // nothing happening
        "3, 5, 1.00",     // more drivers than requests
        "5, 5, 1.00",     // balanced: ratio == threshold
        "6, 4, 1.30",     // ratio 1.5 -> 1 + 0.5 * 0.5 = 1.25 -> nearest 0.1 (HALF_UP) = 1.3
        "8, 2, 2.50",     // ratio 4 -> 2.5 (at cap)
        "50, 1, 2.50",    // clamped to max
        "3, 0, 2.00"      // no supply counts as one driver: ratio 3 -> 2.0
    })
    void multiplierFollowsDemandOverSupply(long demand, long supply, String expected) {
        assertThat(calculator.multiplier(demand, supply)).isEqualByComparingTo(expected);
    }
}
