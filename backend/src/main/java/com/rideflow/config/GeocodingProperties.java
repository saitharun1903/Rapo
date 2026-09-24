package com.rideflow.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Place search and reverse geocoding. The public Nominatim server's usage policy requires an identifying
 * User-Agent, at most one request per second and caching; all three are enforced (docs/architecture.md
 * section 18). {@code disabled} turns the endpoints into 503s (tests, offline development).
 *
 * @param countryCodes     ISO 3166-1 alpha-2 codes results are restricted to (comma-separated), e.g. {@code in}
 * @param biasBoxDegrees   half-size of the box around the caller that results are biased towards (not limited to)
 */
@Validated
@ConfigurationProperties("rideflow.geocoding")
public record GeocodingProperties(
        @NotNull Provider provider,
        @NotBlank String baseUrl,
        @NotBlank String userAgent,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(1) @Max(10) int resultLimit,
        @NotBlank String countryCodes,
        @DecimalMin("0.01") double biasBoxDegrees) {

    public enum Provider {
        NOMINATIM,
        DISABLED
    }
}
