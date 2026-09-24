package com.rideflow.dto.user;

import com.rideflow.entity.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserStatusUpdateRequest(
        @NotNull UserStatus status,
        @NotBlank @Size(max = 255) String reason) {
}
