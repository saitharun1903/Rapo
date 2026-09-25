package com.rideflow.dto.user;

import com.rideflow.entity.Role;
import com.rideflow.entity.UserStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record UserResponse(
        UUID id,
        String email,
        @Nullable String phone,
        String fullName,
        Role role,
        UserStatus status,
        Instant createdAt) {
}
