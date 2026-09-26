package com.rideflow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** In-ride chat: messages are deleted {@code retention} after the ride ends, by a job on {@code cleanupCron}. */
@Validated
@ConfigurationProperties("rideflow.chat")
public record ChatProperties(@NotNull Duration retention, @NotBlank String cleanupCron) {
}
