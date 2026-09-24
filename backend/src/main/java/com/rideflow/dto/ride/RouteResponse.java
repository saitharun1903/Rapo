package com.rideflow.dto.ride;

import com.rideflow.entity.EstimateSource;
import com.rideflow.geospatial.GeoPoint;
import java.util.List;

public record RouteResponse(int distanceMeters, int durationSeconds, EstimateSource source, List<GeoPoint> path) {
}
