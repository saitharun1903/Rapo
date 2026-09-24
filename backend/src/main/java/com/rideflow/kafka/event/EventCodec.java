package com.rideflow.kafka.event;

import com.rideflow.service.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts domain events to and from the JSON envelope. The payload is the event record itself, so producer
 * and consumer share one definition. Unknown fields are ignored when reading, which lets producers add fields
 * without a schema version change while older consumers are still running.
 */
@Component
public class EventCodec {

    /** Envelope and payload format version this build writes and reads. */
    public static final int SCHEMA_VERSION = 1;

    private final JsonMapper mapper;
    private final KafkaNames names;

    public EventCodec(JsonMapper jsonMapper, KafkaNames names) {
        this.mapper = jsonMapper.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        this.names = names;
    }

    public String encode(UUID eventId, DomainEvent event, Instant occurredAt, String traceId) {
        EventTopic topic = EventTopic.of(event);
        EventEnvelope envelope = new EventEnvelope(eventId, topic.eventType(), SCHEMA_VERSION, occurredAt,
                event.aggregateId(), event.aggregateVersion(), traceId, mapper.valueToTree(event));
        return mapper.writeValueAsString(envelope);
    }

    /**
     * @param topicName the deployed topic the record was read from
     * @throws EventDecodingException if the record is not a valid event for that topic
     */
    public ReceivedEvent<DomainEvent> decode(String topicName, String json) {
        EventTopic topic = names.fromName(topicName)
                .orElseThrow(() -> new EventDecodingException("Record from unknown topic " + topicName));
        EventEnvelope envelope = readEnvelope(json);
        if (envelope.schemaVersion() != SCHEMA_VERSION) {
            throw new EventDecodingException("Unsupported schema version " + envelope.schemaVersion()
                    + " for " + topic.eventType() + " (this build reads " + SCHEMA_VERSION + ")");
        }
        if (!topic.eventType().equals(envelope.eventType())) {
            throw new EventDecodingException("Event type " + envelope.eventType() + " on topic " + topicName);
        }
        DomainEvent payload;
        try {
            payload = mapper.treeToValue(envelope.payload(), topic.payloadType());
        } catch (JacksonException ex) {
            throw new EventDecodingException("Invalid " + topic.eventType() + " payload: " + ex.getOriginalMessage(), ex);
        }
        if (payload == null || payload.aggregateId() == null) {
            throw new EventDecodingException(topic.eventType() + " payload has no aggregate id");
        }
        return new ReceivedEvent<>(envelope.eventId(), topic, envelope.traceId(), payload);
    }

    private EventEnvelope readEnvelope(String json) {
        EventEnvelope envelope;
        try {
            envelope = json == null ? null : mapper.readValue(json, EventEnvelope.class);
        } catch (JacksonException ex) {
            throw new EventDecodingException("Not an event envelope: " + ex.getOriginalMessage(), ex);
        }
        if (envelope == null || envelope.eventId() == null || envelope.eventType() == null
                || envelope.payload() == null || envelope.payload().isNull()) {
            throw new EventDecodingException("Event envelope is missing eventId, eventType or payload");
        }
        return envelope;
    }
}
