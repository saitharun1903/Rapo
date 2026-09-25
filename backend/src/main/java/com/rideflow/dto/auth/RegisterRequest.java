package com.rideflow.dto.auth;

import com.rideflow.dto.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @StrongPassword String password,
        @NotBlank @Size(max = 120) String fullName,
        @Pattern(regexp = ValidationPatterns.E164_PHONE, message = "must be in E.164 format, e.g. +919876543210")
        @Nullable String phone,
        @NotNull AccountType accountType) {

    @Override
    public String toString() {
        return "RegisterRequest[accountType=" + accountType + "]";
    }
}
