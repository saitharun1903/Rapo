package com.rideflow.dto.auth;

import com.rideflow.dto.user.UserResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

    public static final String BEARER = "Bearer";

    @Override
    public String toString() {
        return "AuthResponse[user=" + user.id() + ", expiresIn=" + expiresIn + "]";
    }
}
