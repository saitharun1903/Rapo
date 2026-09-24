package com.rideflow.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rideflow.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull RefreshToken refreshToken,
        @Min(4) @Max(31) int bcryptStrength) {

    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl) {
    }

    public record RefreshToken(
            @NotNull Duration ttl,
            @NotNull Duration cleanupRetention,
            @NotBlank String cookieName,
            boolean cookieSecure,
            @NotBlank String cookieSameSite,
            @NotBlank String cookiePath) {
    }
}
