package com.rideflow.dto.driver;

import com.rideflow.entity.DriverAvailability;
import com.rideflow.entity.DriverVerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record DriverResponse(
        UUID id,
        String fullName,
        String email,
        String licenseNumber,
        DriverVerificationStatus verificationStatus,
        @Nullable String rejectionReason,
        @Nullable Instant verifiedAt,
        DriverAvailability availability,
        @Nullable BigDecimal ratingAvg,
        int ratingCount,
        @Nullable VehicleResponse vehicle,
        Instant createdAt) {
}
