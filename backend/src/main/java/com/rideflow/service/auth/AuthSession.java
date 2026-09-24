package com.rideflow.service.auth;

import com.rideflow.dto.auth.AuthResponse;

/** Result of login or refresh: the JSON body plus the raw refresh token destined for the HttpOnly cookie. */
public record AuthSession(AuthResponse response, String refreshToken) {

    @Override
    public String toString() {
        return "AuthSession[" + response + "]";
    }
}
