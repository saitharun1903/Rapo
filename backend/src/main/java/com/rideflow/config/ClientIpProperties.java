package com.rideflow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The client address the frontend vouches for (docs/architecture.md section 9).
 *
 * @param signingSecret HMAC key shared with the frontend ({@code CLIENT_IP_SIGNING_SECRET}); when it is empty,
 *                      {@link ClientIpConfig} is skipped and these properties are never bound
 * @param maxAge        how far a signature's timestamp may be from now, either way (replay window and clock skew)
 */
@Validated
@ConfigurationProperties("rideflow.security.client-ip")
public record ClientIpProperties(@NotBlank String signingSecret, @NotNull Duration maxAge) {
}
