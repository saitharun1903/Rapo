package com.rideflow.service.fare;

import com.rideflow.config.PricingProperties;
import com.rideflow.config.PricingProperties.CategoryRates;
import com.rideflow.entity.FareBreakdown;
import com.rideflow.entity.VehicleCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Pure, deterministic fare calculation used for both quotes and final fares:
 * <pre>
 * subtotal = base + perKm * km + perMinute * minutes
 * total    = roundToIncrement(max(minimumFare, subtotal * surge + bookingFee))
 * </pre>
 * All money is {@link BigDecimal} with HALF_UP rounding; never floating point.
 */
@Component
public class FareCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int KM_SCALE = 3;
    private static final BigDecimal METERS_PER_KM = BigDecimal.valueOf(1000);
    private static final BigDecimal SECONDS_PER_MINUTE = BigDecimal.valueOf(60);

    private final PricingProperties pricing;

    public FareCalculator(PricingProperties pricing) {
        Set<VehicleCategory> missing = EnumSet.allOf(VehicleCategory.class);
        missing.removeAll(pricing.categories().keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("rideflow.pricing.categories is missing rates for " + missing);
        }
        this.pricing = pricing;
    }

    public FareBreakdown.Amounts calculate(
            VehicleCategory category, int distanceMeters, int durationSeconds, BigDecimal surgeMultiplier) {
        CategoryRates rates = pricing.categories().get(category);
        BigDecimal km = BigDecimal.valueOf(distanceMeters).divide(METERS_PER_KM, KM_SCALE, RoundingMode.HALF_UP);
        BigDecimal minutes = BigDecimal.valueOf(durationSeconds).divide(SECONDS_PER_MINUTE, MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal baseFare = money(rates.baseFare());
        BigDecimal distanceCharge = money(rates.perKm().multiply(km));
        BigDecimal timeCharge = money(rates.perMinute().multiply(minutes));
        BigDecimal subtotal = baseFare.add(distanceCharge).add(timeCharge);
        BigDecimal surge = surgeMultiplier.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal bookingFee = money(rates.bookingFee());
        BigDecimal minimumFare = money(rates.minimumFare());

        BigDecimal metered = money(subtotal.multiply(surge)).add(bookingFee);
        boolean minimumApplied = metered.compareTo(minimumFare) < 0;
        BigDecimal total = roundToIncrement(minimumApplied ? minimumFare : metered);

        return new FareBreakdown.Amounts(baseFare, distanceCharge, timeCharge, subtotal, surge, bookingFee,
                minimumFare, minimumApplied, total, pricing.currency(), distanceMeters, durationSeconds,
                pricing.version());
    }

    private BigDecimal roundToIncrement(BigDecimal amount) {
        BigDecimal increment = pricing.roundingIncrement();
        return amount.divide(increment, 0, RoundingMode.HALF_UP).multiply(increment).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
