package com.rideflow.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Road routing. With {@code provider=osrm} the OSRM HTTP API is called and the straight-line estimate is
 * only a fallback when it fails; {@code straight-line} skips the network entirely (tests, offline dev).
 */
@Validated
@ConfigurationProperties("rideflow.routing")
public record RoutingProperties(
        @NotNull Provider provider,
        @NotBlank String osrmBaseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @DecimalMin("1.0") double circuityFactor,
        @DecimalMin("1.0") double averageSpeedKmh) {

    public enum Provider {
        OSRM,
        STRAIGHT_LINE
    }
}
