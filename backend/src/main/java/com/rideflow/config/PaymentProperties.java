package com.rideflow.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param platformFeeRate the platform's share of each fare (0.20 = 20 %); the driver earns the rest
 */
@Validated
@ConfigurationProperties("rideflow.payments")
public record PaymentProperties(@NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal platformFeeRate) {
}
