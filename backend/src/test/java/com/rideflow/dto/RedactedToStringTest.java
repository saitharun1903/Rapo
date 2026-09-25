package com.rideflow.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideflow.dto.auth.AccountType;
import com.rideflow.dto.auth.AuthResponse;
import com.rideflow.dto.auth.LoginRequest;
import com.rideflow.dto.auth.RegisterRequest;
import com.rideflow.dto.user.ChangePasswordRequest;
import com.rideflow.dto.user.UserResponse;
import com.rideflow.entity.Role;
import com.rideflow.entity.UserStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Records print every component by default, and a request object ends up in a log line or an exception message
 * sooner or later. These carry passwords, tokens or personal details, so their toString must leave them out.
 */
class RedactedToStringTest {

    private static final String EMAIL = "asha@example.com";
    private static final String PHONE = "+919876543210";
    private static final String PASSWORD = "Correct-horse-9";
    private static final String NEW_PASSWORD = "Battery-staple-7";
    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.payload.signature";

    @Test
    void loginRequestsHideTheCredentials() {
        assertThat(new LoginRequest(EMAIL, PASSWORD).toString()).doesNotContain(EMAIL, PASSWORD);
    }

    @Test
    void registrationRequestsHideTheCredentialsAndPersonalDetails() {
        String text = new RegisterRequest(EMAIL, PASSWORD, "Asha Rao", PHONE, AccountType.DRIVER).toString();
        assertThat(text).doesNotContain(EMAIL, PASSWORD, "Asha Rao", PHONE).contains("DRIVER");
    }

    @Test
    void passwordChangesHideBothPasswords() {
        assertThat(new ChangePasswordRequest(PASSWORD, NEW_PASSWORD).toString()).doesNotContain(PASSWORD, NEW_PASSWORD);
    }

    @Test
    void authResponsesHideTheAccessTokenAndThePersonalDetails() {
        UUID userId = UUID.randomUUID();
        UserResponse user = new UserResponse(userId, EMAIL, PHONE, "Asha Rao", Role.PASSENGER, UserStatus.ACTIVE,
                Instant.parse("2026-09-25T10:00:00Z"));
        String text = new AuthResponse(TOKEN, AuthResponse.BEARER, 900, user).toString();
        assertThat(text).doesNotContain(TOKEN, EMAIL, PHONE, "Asha Rao").contains(userId.toString());
    }
}
