package com.rideflow.kafka.event;

import com.rideflow.service.event.DomainEvent;
import java.util.UUID;

/** A decoded Kafka event: the envelope's metadata and its typed payload. */
public record ReceivedEvent<T extends DomainEvent>(UUID eventId, EventTopic topic, String traceId, T payload) {
}
