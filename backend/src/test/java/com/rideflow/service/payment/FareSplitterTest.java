package com.rideflow.service.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideflow.config.PaymentProperties;
import com.rideflow.entity.Payment;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FareSplitterTest {

    private final FareSplitter splitter = new FareSplitter(new PaymentProperties(new BigDecimal("0.20")));

    @Test
    void platformTakesItsRateAndTheDriverTheRest() {
        Payment.Split split = splitter.split(new BigDecimal("245.00"), "INR");

        assertThat(split.amount()).isEqualByComparingTo("245.00");
        assertThat(split.platformFee()).isEqualByComparingTo("49.00");
        assertThat(split.driverEarnings()).isEqualByComparingTo("196.00");
        assertThat(split.currency()).isEqualTo("INR");
    }

    /** The fee is rounded; the driver gets the exact remainder, so the parts always add up. */
    @ParameterizedTest
    @CsvSource({"80.01, 16.00, 64.01", "80.03, 16.01, 64.02", "0.01, 0.00, 0.01", "0.00, 0.00, 0.00"})
    void partsAlwaysAddUpToTheFare(String fare, String fee, String earnings) {
        Payment.Split split = splitter.split(new BigDecimal(fare), "INR");

        assertThat(split.platformFee()).isEqualByComparingTo(fee);
        assertThat(split.driverEarnings()).isEqualByComparingTo(earnings);
        assertThat(split.platformFee().add(split.driverEarnings())).isEqualByComparingTo(fare);
        assertThat(split.platformFee().scale()).isEqualTo(2);
    }
}
