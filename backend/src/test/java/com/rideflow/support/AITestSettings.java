package com.rideflow.support;

import com.rideflow.config.AIProperties;
import java.math.BigDecimal;
import java.time.Duration;

/** {@link AIProperties} for tests, matching application.yml except where a test needs something else. */
public final class AITestSettings {

    public static final Duration TIMEOUT = Duration.ofSeconds(2);

    private AITestSettings() {
    }

    public static AIProperties properties(AIProperties.Provider provider, String baseUrl, String model,
                                          Duration timeout, AIProperties.Resilience resilience) {
        return new AIProperties(provider,
                new AIProperties.Local(baseUrl, model, 0.2, Duration.ofSeconds(2), timeout, 1024),
                new AIProperties.External("test-key-not-a-secret", baseUrl, model, "low", timeout, 4096),
                resilience,
                observations(),
                new AIProperties.Validation(0.01),
                Duration.ofMinutes(15));
    }

    public static AIProperties.Resilience resilience(int maxAttempts, int slidingWindowSize, int maxConcurrentCalls) {
        return new AIProperties.Resilience(maxAttempts, Duration.ofMillis(10), Duration.ofSeconds(10), 50,
                slidingWindowSize, Duration.ofSeconds(60), maxConcurrentCalls);
    }

    public static AIProperties.Observations observations() {
        return new AIProperties.Observations(BigDecimal.valueOf(5), BigDecimal.valueOf(10), BigDecimal.valueOf(20),
                new BigDecimal("1.5"), 3, 20, BigDecimal.valueOf(20));
    }
}
