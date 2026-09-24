package com.rideflow.dto.driver;

import com.rideflow.entity.VehicleCategory;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String make,
        String model,
        String color,
        String plateNumber,
        short modelYear,
        VehicleCategory category,
        short seats) {
}
