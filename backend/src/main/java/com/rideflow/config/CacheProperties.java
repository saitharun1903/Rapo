package com.rideflow.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Redis cache settings. Why each cache exists and how it is invalidated: docs/architecture.md section 9.
 *
 * @param enabled         {@code false} bypasses every cache (used to measure the uncached baseline)
 * @param routeTtl        routed road distance/duration between two points
 * @param geocodeTtl      geocoding results (Nominatim's usage policy requires caching)
 * @param surgeTtl        surge multiplier per ~1 km cell
 * @param etaTtl          live ETA of a ride's driver to its next stop
 * @param redisRetryAfter after a Redis failure, skip Redis for this long before trying again
 */
@Validated
@ConfigurationProperties("rideflow.cache")
public record CacheProperties(
        boolean enabled,
        @NotNull Duration routeTtl,
        @NotNull Duration geocodeTtl,
        @NotNull Duration surgeTtl,
        @NotNull Duration etaTtl,
        @NotNull Duration redisRetryAfter) {
}
