package com.rideflow.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Demand/supply surge: {@code multiplier = clamp(1 + sensitivity * max(0, demand/supply - threshold), 1, max)},
 * rounded to {@code step}. Demand = open requests near the pickup within {@code demandWindow}; supply =
 * fresh available drivers within {@code radiusMeters}.
 */
@Validated
@ConfigurationProperties("rideflow.surge")
public record SurgeProperties(
        boolean enabled,
        @Min(100) int radiusMeters,
        @NotNull Duration demandWindow,
        @NotNull @DecimalMin("0") BigDecimal sensitivity,
        @NotNull @DecimalMin("0") BigDecimal threshold,
        @NotNull @DecimalMin("1") BigDecimal maxMultiplier,
        @NotNull @DecimalMin("0.01") BigDecimal step) {
}
