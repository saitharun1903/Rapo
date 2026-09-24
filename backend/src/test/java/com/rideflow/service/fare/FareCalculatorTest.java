package com.rideflow.service.fare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.config.PricingProperties;
import com.rideflow.config.PricingProperties.CategoryRates;
import com.rideflow.entity.FareBreakdown;
import com.rideflow.entity.VehicleCategory;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FareCalculatorTest {

    private static final CategoryRates ECONOMY = rates("40", "12", "1.5", "10", "80");

    private static CategoryRates rates(String base, String perKm, String perMinute, String bookingFee, String minimum) {
        return new CategoryRates(new BigDecimal(base), new BigDecimal(perKm), new BigDecimal(perMinute),
                new BigDecimal(bookingFee), new BigDecimal(minimum));
    }

    private static FareCalculator calculator(String roundingIncrement) {
        return new FareCalculator(new PricingProperties("INR", "test-v1", new BigDecimal(roundingIncrement), Map.of(
                VehicleCategory.ECONOMY, ECONOMY,
                VehicleCategory.COMFORT, rates("60", "16", "2", "15", "120"),
                VehicleCategory.XL, rates("90", "22", "2.5", "20", "180"))));
    }

    @Test
    void computesEachLineItem() {
        // 12.84 km, 28m40s (28.67 min), surge 1.2
        FareBreakdown.Amounts fare = calculator("0.01").calculate(VehicleCategory.ECONOMY, 12_840, 1_720, new BigDecimal("1.2"));

        assertThat(fare.baseFare()).isEqualByComparingTo("40.00");
        assertThat(fare.distanceCharge()).isEqualByComparingTo("154.08");   // 12 * 12.840
        assertThat(fare.timeCharge()).isEqualByComparingTo("43.01");        // 1.5 * 28.67 = 43.005 -> 43.01
        assertThat(fare.subtotal()).isEqualByComparingTo("237.09");
        assertThat(fare.surgeMultiplier()).isEqualByComparingTo("1.20");
        // 237.09 * 1.2 = 284.508 -> 284.51, + 10 booking fee
        assertThat(fare.total()).isEqualByComparingTo("294.51");
        assertThat(fare.minimumFareApplied()).isFalse();
        assertThat(fare.currency()).isEqualTo("INR");
        assertThat(fare.pricingVersion()).isEqualTo("test-v1");
        assertThat(fare.distanceMeters()).isEqualTo(12_840);
    }

    @Test
    void roundsTotalToConfiguredIncrement() {
        FareBreakdown.Amounts fare = calculator("1.00").calculate(VehicleCategory.ECONOMY, 12_840, 1_720, new BigDecimal("1.2"));

        assertThat(fare.total()).isEqualByComparingTo("295.00");
    }

    @Test
    void appliesMinimumFareToShortTrips() {
        FareBreakdown.Amounts fare = calculator("1.00").calculate(VehicleCategory.ECONOMY, 500, 120, BigDecimal.ONE);

        // 40 + 6 + 3 + 10 = 59 < 80
        assertThat(fare.minimumFareApplied()).isTrue();
        assertThat(fare.total()).isEqualByComparingTo("80.00");
    }

    @Test
    void surgeScalesMeteredPartButNotBookingFee() {
        FareCalculator calc = calculator("0.01");
        FareBreakdown.Amounts normal = calc.calculate(VehicleCategory.COMFORT, 10_000, 1_200, BigDecimal.ONE);
        FareBreakdown.Amounts surged = calc.calculate(VehicleCategory.COMFORT, 10_000, 1_200, new BigDecimal("2.0"));

        BigDecimal bookingFee = new BigDecimal("15.00");
        assertThat(surged.total().subtract(bookingFee))
                .isEqualByComparingTo(normal.total().subtract(bookingFee).multiply(BigDecimal.TWO));
    }

    @Test
    void refusesConfigurationMissingACategory() {
        assertThatThrownBy(() -> new FareCalculator(new PricingProperties("INR", "v", BigDecimal.ONE,
                Map.of(VehicleCategory.ECONOMY, ECONOMY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMFORT");
    }
}
