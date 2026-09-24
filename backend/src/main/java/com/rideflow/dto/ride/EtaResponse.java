package com.rideflow.dto.ride;

import com.rideflow.entity.EstimateSource;
import java.time.Instant;

/**
 * Time and distance from the driver to the ride's next stop, as of {@code computedAt}. Clients show
 * {@code seconds} minus the time elapsed since {@code computedAt}; it is refreshed at most every 30 s.
 *
 * @param source whether the road router or the straight-line fallback produced the numbers
 */
public record EtaResponse(Target target, int seconds, int distanceMeters, EstimateSource source, Instant computedAt) {

    public enum Target {
        PICKUP,
        DROPOFF
    }
}
