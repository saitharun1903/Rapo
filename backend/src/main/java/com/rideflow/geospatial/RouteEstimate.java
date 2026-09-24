package com.rideflow.geospatial;

import com.rideflow.entity.EstimateSource;
import java.util.List;

/** Road distance/duration between two points, the path to draw, and how it was obtained. */
public record RouteEstimate(int distanceMeters, int durationSeconds, List<GeoPoint> path, EstimateSource source) {

    public RouteEstimate {
        path = List.copyOf(path);
    }
}
