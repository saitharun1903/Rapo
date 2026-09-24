package com.rideflow.config;

import com.rideflow.entity.VehicleCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Per-category rate card. {@code version} is stored with every fare breakdown so historical fares stay
 * explainable after rates change; bump it whenever rates change.
 */
@Validated
@ConfigurationProperties("rideflow.pricing")
public record PricingProperties(
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotBlank @Size(max = 20) String version,
        @NotNull @DecimalMin("0.01") BigDecimal roundingIncrement,
        @NotEmpty Map<VehicleCategory, @Valid CategoryRates> categories) {

    public record CategoryRates(
            @NotNull @DecimalMin("0") BigDecimal baseFare,
            @NotNull @DecimalMin("0") BigDecimal perKm,
            @NotNull @DecimalMin("0") BigDecimal perMinute,
            @NotNull @DecimalMin("0") BigDecimal bookingFee,
            @NotNull @DecimalMin("0") BigDecimal minimumFare) {
    }
}
