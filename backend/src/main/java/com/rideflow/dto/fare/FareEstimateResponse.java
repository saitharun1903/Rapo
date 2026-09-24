package com.rideflow.dto.fare;

import com.rideflow.entity.EstimateSource;
import com.rideflow.geospatial.GeoPoint;
import java.util.List;

public record FareEstimateResponse(
        int distanceMeters,
        int durationSeconds,
        EstimateSource estimateSource,
        String surgeMultiplier,
        List<GeoPoint> route,
        List<FareQuoteResponse> quotes) {
}
