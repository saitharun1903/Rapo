package com.rideflow.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The outbox relay (docs/architecture.md section 10).
 *
 * @param relayEnabled {@code false} leaves events in the outbox (for tests that inspect it)
 * @param batchSize    rows sent per relay transaction
 * @param pollInterval longest wait between relay runs; a commit that wrote events wakes the relay at once
 * @param sendTimeout  how long a batch waits for the broker's acknowledgements before retrying later
 * @param errorBackoff wait after a run failed outright (for example the database was unreachable)
 * @param retention    published rows are purged after this long
 */
@Validated
@ConfigurationProperties("rideflow.outbox")
public record OutboxProperties(
        boolean relayEnabled,
        @Min(1) int batchSize,
        @NotNull Duration pollInterval,
        @NotNull Duration sendTimeout,
        @NotNull Duration errorBackoff,
        @NotNull Duration retention) {
}
