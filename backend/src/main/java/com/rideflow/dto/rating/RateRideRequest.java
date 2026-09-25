package com.rideflow.dto.rating;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record RateRideRequest(
        @NotNull @Min(1) @Max(5) Integer score,
        @Size(max = 500) @Nullable String comment) {
}
