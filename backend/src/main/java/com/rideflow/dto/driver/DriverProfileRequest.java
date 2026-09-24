package com.rideflow.dto.driver;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DriverProfileRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{6,40}$", message = "must be 6-40 letters, digits or dashes")
        String licenseNumber,
        @NotNull @Valid VehicleRequest vehicle) {
}
