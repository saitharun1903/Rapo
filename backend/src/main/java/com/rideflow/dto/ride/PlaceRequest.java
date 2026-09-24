package com.rideflow.dto.ride;

import com.rideflow.geospatial.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlaceRequest(@NotNull @Valid GeoPoint point, @NotBlank @Size(max = 255) String address) {
}
