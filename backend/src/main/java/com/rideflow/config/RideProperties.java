package com.rideflow.config;

import com.rideflow.geospatial.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rideflow.ride")
public record RideProperties(
        @Valid @NotNull ServiceArea serviceArea,
        @Min(1) int minTripDistanceMeters,
        @NotNull Duration quoteTtl,
        @Min(1) int quoteMatchToleranceMeters,
        @Min(1) int pickupGeofenceMeters,
        @NotNull Duration noShowWait,
        @Valid @NotNull Location location) {

    /** Rides are accepted only when pickup and dropoff are within {@code radiusMeters} of {@code center}. */
    public record ServiceArea(@Valid @NotNull GeoPoint center, @Min(1) int radiusMeters) {
    }

    /**
     * Driver location rules: reports older than {@code maxAge} or more than {@code maxFutureSkew} ahead of
     * server time are rejected; trip track points are sampled at most every {@code trackMinInterval} unless
     * the driver moved at least {@code trackMinDistanceMeters}.
     */
    public record Location(
            @NotNull Duration maxAge,
            @NotNull Duration maxFutureSkew,
            @NotNull Duration trackMinInterval,
            @Min(1) int trackMinDistanceMeters) {
    }
}
