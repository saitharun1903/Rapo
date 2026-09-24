package com.rideflow.dto.driver;

import com.rideflow.geospatial.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** One GPS report from a driver device. {@code recordedAt} is device time and is checked for skew. */
public record LocationUpdateRequest(
        @NotNull @Valid GeoPoint location,
        @Min(0) @Max(359) Integer headingDeg,
        @DecimalMin("0.0") @DecimalMax("100.0") Double speedMps,
        @DecimalMin("0.0") @DecimalMax("10000.0") Double accuracyMeters,
        @NotNull Instant recordedAt) {
}
