package com.rideflow.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String password) {

    @Override
    public String toString() {
        return "LoginRequest[credentials redacted]";
    }
}
