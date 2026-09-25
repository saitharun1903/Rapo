package com.rideflow.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AI trip insights (docs/architecture.md section 12). Secrets come only from the environment
 * ({@code ANTHROPIC_API_KEY}); nothing here has a key default.
 *
 * @param provider       {@code local} (Ollama), {@code external} (Anthropic) or {@code disabled}
 * @param local          Ollama server and model
 * @param external       Anthropic API settings
 * @param resilience     retries, circuit breaker and concurrency limit around every provider call
 * @param observations   thresholds for the deterministic (non-AI) observations
 * @param validation     how strictly AI output must match the supplied facts
 * @param pendingTimeout an analysis still PENDING after this long (its instance died) may be regenerated
 */
@Validated
@ConfigurationProperties("rideflow.ai")
public record AIProperties(
        @NotNull Provider provider,
        @Valid @NotNull Local local,
        @Valid @NotNull External external,
        @Valid @NotNull Resilience resilience,
        @Valid @NotNull Observations observations,
        @Valid @NotNull Validation validation,
        @NotNull Duration pendingTimeout) {

    public enum Provider {
        LOCAL, EXTERNAL, DISABLED
    }

    /** Fail at startup rather than on the first ride when the external provider has no key. */
    @AssertTrue(message = "rideflow.ai.external.api-key (ANTHROPIC_API_KEY) is required when AI_PROVIDER=external")
    public boolean isExternalKeyPresentWhenSelected() {
        return provider != Provider.EXTERNAL || (external.apiKey() != null && !external.apiKey().isBlank());
    }

    /**
     * @param temperature low values keep answers close to the facts
     * @param timeout     one request, including generation (CPU inference of a small model can take a minute)
     */
    public record Local(
            @NotBlank String baseUrl,
            @NotBlank String model,
            @DecimalMin("0.0") double temperature,
            @NotNull Duration connectTimeout,
            @NotNull Duration timeout,
            @Min(1) int maxOutputTokens) {
    }

    /**
     * @param effort output effort ({@code low} … {@code max}); trip insights are short, so {@code low} is enough
     */
    public record External(
            String apiKey,
            @NotBlank String baseUrl,
            @NotBlank String model,
            @NotBlank String effort,
            @NotNull Duration timeout,
            @Min(1) int maxOutputTokens) {
    }

    /**
     * @param maxAttempts          attempts per call for server errors and network failures (timeouts are not retried)
     * @param backoff              wait before the first retry, doubled for each further one
     * @param maxRetryAfter        a 429 is retried once if the provider's Retry-After is at most this
     * @param failureRateThreshold circuit opens when this percentage of the recent calls failed
     * @param slidingWindowSize    number of recent calls the failure rate is computed over
     * @param openDuration         calls are refused (UNAVAILABLE) for this long before a trial call
     * @param maxConcurrentCalls   more simultaneous calls than this are refused (BUSY)
     */
    public record Resilience(
            @Min(1) int maxAttempts,
            @NotNull Duration backoff,
            @NotNull Duration maxRetryAfter,
            @Min(1) int failureRateThreshold,
            @Min(1) int slidingWindowSize,
            @NotNull Duration openDuration,
            @Min(1) int maxConcurrentCalls) {
    }

    /**
     * @param fareDeltaPercent         final vs estimated fare difference worth pointing out
     * @param distanceDeltaPercent     actual vs estimated distance difference worth pointing out
     * @param durationDeltaPercent     actual vs estimated duration difference worth pointing out
     * @param detourRatio              driven distance / straight-line distance above which the route counts as a detour
     * @param historyMinTrips          earlier completed trips needed before comparing with the passenger's history
     * @param historyMaxTrips          most recent completed trips the history averages are taken over
     * @param historyFarePerKmDeltaPercent fare per km above the passenger's average worth pointing out
     */
    public record Observations(
            @DecimalMin("0.0") BigDecimal fareDeltaPercent,
            @DecimalMin("0.0") BigDecimal distanceDeltaPercent,
            @DecimalMin("0.0") BigDecimal durationDeltaPercent,
            @DecimalMin("1.0") BigDecimal detourRatio,
            @Min(1) int historyMinTrips,
            @Min(1) int historyMaxTrips,
            @DecimalMin("0.0") BigDecimal historyFarePerKmDeltaPercent) {
    }

    /** @param numericTolerance relative tolerance for a number in AI text to count as one of the facts */
    public record Validation(@DecimalMin("0.0") double numericTolerance) {
    }
}
