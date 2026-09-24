package com.rideflow.dto.user;

import com.rideflow.entity.Role;
import com.rideflow.entity.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String phone,
        String fullName,
        Role role,
        UserStatus status,
        Instant createdAt) {
}
