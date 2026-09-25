package com.rideflow.dto.ride;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record CancelRideRequest(@Size(max = 255) @Nullable String reason) {
}
