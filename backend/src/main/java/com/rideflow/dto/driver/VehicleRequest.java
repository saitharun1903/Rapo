package com.rideflow.dto.driver;

import com.rideflow.entity.VehicleCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The upper bound of {@code modelYear} depends on today's date and is checked in the service. */
public record VehicleRequest(
        @NotBlank @Size(max = 40) String make,
        @NotBlank @Size(max = 40) String model,
        @NotBlank @Size(max = 30) String color,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9 -]{4,20}$", message = "must be 4-20 letters, digits, spaces or dashes")
        String plateNumber,
        @NotNull @Min(1990) Short modelYear,
        @NotNull VehicleCategory category,
        @NotNull @Min(2) @Max(8) Short seats) {
}
