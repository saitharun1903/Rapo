package com.rideflow.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Driver matching tunables. Round {@code n} searches {@code initialRadius * radiusGrowth^(n-1)} metres
 * (capped at {@code maxRadius}) and offers the ride to up to {@code offersPerRound} of the nearest
 * {@code candidateLimit} drivers; the first to accept wins. See docs/architecture.md section 7.
 */
@Validated
@ConfigurationProperties("rideflow.matching")
public record MatchingProperties(
        @Min(100) int initialRadiusMeters,
        @DecimalMin("1.0") double radiusGrowth,
        @Min(100) int maxRadiusMeters,
        @Min(1) @Max(10) int maxRounds,
        @Min(1) @Max(100) int candidateLimit,
        @Min(1) @Max(10) int offersPerRound,
        @NotNull Duration offerTtl,
        @NotNull Duration locationFreshness,
        @NotNull Duration triggerGrace,
        @Min(1) int sweepBatchSize) {
}
