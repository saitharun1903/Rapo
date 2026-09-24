package com.rideflow.dto.driver;

import com.rideflow.geospatial.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** A driver must report where they are when going online, so matching can find them immediately. */
public record GoOnlineRequest(@NotNull @Valid GeoPoint location) {
}
