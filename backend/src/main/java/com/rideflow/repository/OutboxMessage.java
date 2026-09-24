package com.rideflow.repository;

import java.util.UUID;

/**
 * One outbox row: a Kafka record waiting to be sent.
 *
 * @param id      the event id, which is also the {@code eventId} in the envelope
 * @param payload the complete JSON envelope
 */
public record OutboxMessage(UUID id, String topic, String key, String eventType, String payload) {
}
