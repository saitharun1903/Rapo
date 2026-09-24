package com.rideflow.dto.driver;

import com.rideflow.entity.DriverAvailability;
import com.rideflow.entity.DriverVerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DriverResponse(
        UUID id,
        String fullName,
        String email,
        String licenseNumber,
        DriverVerificationStatus verificationStatus,
        String rejectionReason,
        Instant verifiedAt,
        DriverAvailability availability,
        BigDecimal ratingAvg,
        int ratingCount,
        VehicleResponse vehicle,
        Instant createdAt) {
}
