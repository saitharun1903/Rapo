package com.rideflow.dto.admin;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Live operational state of the instance that served the request. Metric values are {@code null} when the
 * metric is not registered (for example before the first dead letter).
 *
 * @param health             overall health status ({@code UP}, {@code DOWN}, ...)
 * @param components         status of each health component
 * @param outboxPending      events written but not yet acknowledged by Kafka
 * @param deadLettersByTopic records dead-lettered since this instance started, by original topic
 * @param aiCircuitOpen      {@code true} while AI calls are refused after repeated provider failures
 * @param aiCallsActive      AI calls in flight on this instance
 * @param webSocketSessions  open STOMP sessions on this instance
 * @param redisAvailable     {@code false} while Redis is being bypassed
 */
public record SystemStatusResponse(
        String health,
        Map<String, String> components,
        @Nullable Long outboxPending,
        Map<String, Long> deadLettersByTopic,
        @Nullable Boolean aiCircuitOpen,
        @Nullable Long aiCallsActive,
        @Nullable Long webSocketSessions,
        @Nullable Boolean redisAvailable) {
}
