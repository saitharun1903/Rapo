package com.rideflow.kafka.event;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * The JSON wrapped around every Kafka event (docs/events.md section 1.1).
 *
 * @param eventId          unique per event; consumers use it for idempotency (also the outbox row id)
 * @param schemaVersion    changes only for breaking payload changes; added fields do not change it
 * @param aggregateVersion the aggregate's version after the change, where it has one
 * @param traceId          the request's trace id, so logs of the consumer can be correlated with the producer
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID aggregateId,
        Long aggregateVersion,
        String traceId,
        JsonNode payload) {
}
