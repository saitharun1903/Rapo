package com.rideflow.dto.driver;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason recorded when an admin rejects or suspends a driver. */
public record DriverDecisionRequest(@NotBlank @Size(max = 255) String reason) {
}
