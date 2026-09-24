package com.rideflow.service.fare;

import com.rideflow.config.SurgeProperties;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Pure demand/supply surge formula; see {@link SurgeProperties} for the definition. */
@Component
public class SurgeCalculator {

    private static final int MULTIPLIER_SCALE = 2;
    private static final long MIN_SUPPLY = 1;

    private final SurgeProperties properties;

    public SurgeCalculator(SurgeProperties properties) {
        this.properties = properties;
    }

    public BigDecimal multiplier(long openRequests, long availableDrivers) {
        BigDecimal ratio = BigDecimal.valueOf(openRequests)
                .divide(BigDecimal.valueOf(Math.max(MIN_SUPPLY, availableDrivers)), MathContext.DECIMAL64);
        BigDecimal excess = ratio.subtract(properties.threshold()).max(BigDecimal.ZERO);
        BigDecimal raw = BigDecimal.ONE.add(properties.sensitivity().multiply(excess));
        BigDecimal clamped = raw.min(properties.maxMultiplier()).max(BigDecimal.ONE);
        BigDecimal step = properties.step();
        return clamped.divide(step, 0, RoundingMode.HALF_UP).multiply(step).setScale(MULTIPLIER_SCALE, RoundingMode.HALF_UP);
    }
}
