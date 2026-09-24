package com.rideflow.config;

import com.rideflow.cache.RateLimitScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Fixed-window limits per scope: at most {@code limit} requests per {@code window} per subject. */
@Validated
@ConfigurationProperties("rideflow.rate-limit")
public record RateLimitProperties(boolean enabled, @NotNull Map<RateLimitScope, @Valid Rule> rules) {

    public RateLimitProperties {
        // EnumMap(Map) rejects an empty non-EnumMap source.
        rules = rules == null || rules.isEmpty() ? Map.of() : new EnumMap<>(rules);
    }

    public record Rule(@Min(1) int limit, @NotNull Duration window) {
    }

    public Rule rule(RateLimitScope scope) {
        return rules.get(scope);
    }

    /** Every scope must be configured, so a new scope cannot silently go unlimited. */
    @AssertTrue(message = "every RateLimitScope needs a rule under rideflow.rate-limit.rules")
    public boolean isEveryScopeConfigured() {
        return rules.keySet().containsAll(EnumSet.allOf(RateLimitScope.class));
    }
}
