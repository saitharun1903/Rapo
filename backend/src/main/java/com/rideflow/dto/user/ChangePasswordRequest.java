package com.rideflow.dto.user;

import com.rideflow.dto.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 200) String currentPassword,
        @NotNull @StrongPassword String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[redacted]";
    }
}
