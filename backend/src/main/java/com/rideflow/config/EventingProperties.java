package com.rideflow.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kafka topics and consumers (connection settings are Spring Boot's {@code spring.kafka.*}). Topic catalogue
 * and delivery semantics: docs/events.md section 1.
 *
 * @param prefix                   prepended to every topic and consumer group name, so several environments
 *                                 can share one cluster; integration tests use a random prefix per Spring
 *                                 context so that contexts never consume each other's events
 * @param partitions               partitions per topic (ordering is per key, so this caps consumer parallelism)
 * @param replicationFactor        1 for a single local broker; at least 3 on a real cluster
 * @param retention                how long each class of topic keeps its records
 * @param retry                    redelivery of a record whose handler failed, before it goes to the DLT
 * @param processedEventsRetention how long consumer idempotency records are kept; longer than any redelivery
 * @param cleanupCron              when published outbox rows and old idempotency records are purged
 */
@Validated
@ConfigurationProperties("rideflow.kafka")
public record EventingProperties(
        @NotNull String prefix,
        @Min(1) int partitions,
        @Min(1) int replicationFactor,
        @Valid @NotNull Retention retention,
        @Valid @NotNull Retry retry,
        @NotNull Duration processedEventsRetention,
        @NotBlank String cleanupCron) {

    /**
     * @param lifecycle    ride, payment and dispatch events
     * @param location     driver positions (superseded within seconds)
     * @param notification notification requests and fan-out
     * @param deadLetter   {@code <topic>.DLT}: long enough to investigate and replay
     */
    public record Retention(
            @NotNull Duration lifecycle,
            @NotNull Duration location,
            @NotNull Duration notification,
            @NotNull Duration deadLetter) {
    }

    /**
     * @param attempts        redeliveries after the first failure
     * @param initialInterval wait before the first redelivery
     * @param multiplier      growth of the wait between redeliveries
     */
    public record Retry(@Min(0) int attempts, @NotNull Duration initialInterval, @DecimalMin("1.0") double multiplier) {
    }
}
