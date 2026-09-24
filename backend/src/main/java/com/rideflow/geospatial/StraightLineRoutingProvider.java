package com.rideflow.geospatial;

import com.rideflow.config.RoutingProperties;
import com.rideflow.entity.EstimateSource;
import java.util.List;

/**
 * Offline approximation: great-circle distance scaled by a circuity factor (roads are not straight),
 * at a configured average urban speed. Always flagged {@link EstimateSource#APPROXIMATE}.
 */
public class StraightLineRoutingProvider implements RoutingProvider {

    private static final double METERS_PER_KM = 1000.0;
    private static final double SECONDS_PER_HOUR = 3600.0;
    private static final int MIN_DURATION_SECONDS = 1;
    private static final int MIN_DISTANCE_METERS = 1;

    private final double circuityFactor;
    private final double metersPerSecond;

    public StraightLineRoutingProvider(RoutingProperties properties) {
        this.circuityFactor = properties.circuityFactor();
        this.metersPerSecond = properties.averageSpeedKmh() * METERS_PER_KM / SECONDS_PER_HOUR;
    }

    @Override
    public RouteEstimate route(GeoPoint from, GeoPoint to) {
        double distance = GeoMath.haversineMeters(from, to) * circuityFactor;
        int distanceMeters = Math.max(MIN_DISTANCE_METERS, (int) Math.round(distance));
        int durationSeconds = Math.max(MIN_DURATION_SECONDS, (int) Math.round(distance / metersPerSecond));
        return new RouteEstimate(distanceMeters, durationSeconds, List.of(from, to), EstimateSource.APPROXIMATE);
    }
}
