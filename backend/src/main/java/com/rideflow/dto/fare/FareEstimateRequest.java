package com.rideflow.dto.fare;

import com.rideflow.geospatial.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record FareEstimateRequest(@NotNull @Valid GeoPoint pickup, @NotNull @Valid GeoPoint dropoff) {
}
