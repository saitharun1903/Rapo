package com.rideflow.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Real-time channel tunables (STOMP over WebSocket) and driver presence. See docs/architecture.md section 8
 * and docs/events.md section 2.
 *
 * @param heartbeat           STOMP heartbeat in both directions
 * @param locationMinInterval minimum spacing of location messages per session; faster ones are dropped
 * @param connectTimeout      a socket that has not sent an authenticated CONNECT by then is closed
 * @param presence            when silent drivers are taken offline
 */
@Validated
@ConfigurationProperties("rideflow.realtime")
public record RealtimeProperties(
        @NotNull Duration heartbeat,
        @NotNull Duration locationMinInterval,
        @NotNull Duration connectTimeout,
        @Valid @NotNull Presence presence) {

    /**
     * @param timeout        an AVAILABLE driver with no location update for this long is set OFFLINE
     * @param sweepBatchSize drivers handled per sweep, so one sweep stays short
     */
    public record Presence(@NotNull Duration timeout, @Min(1) int sweepBatchSize) {
    }
}
