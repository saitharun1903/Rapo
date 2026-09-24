package com.rideflow.kafka.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.config.EventingProperties;
import com.rideflow.dto.ride.EtaResponse;
import com.rideflow.entity.ActorType;
import com.rideflow.entity.RideStatus;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.service.driver.event.DriverLocationUpdatedEvent;
import com.rideflow.service.ride.EtaDestination;
import com.rideflow.service.ride.event.RideStatusChangedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class EventCodecTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:15:30.123456Z");
    private static final String PREFIX = "test.";

    private final JsonMapper json = JsonMapper.builder().build();
    private final KafkaNames names = new KafkaNames(new EventingProperties(PREFIX, 3, 1,
            new EventingProperties.Retention(Duration.ofDays(7), Duration.ofHours(6), Duration.ofDays(3),
                    Duration.ofDays(14)),
            new EventingProperties.Retry(3, Duration.ofSeconds(1), 2.0), Duration.ofDays(7), "0 30 3 * * *"));
    private final EventCodec codec = new EventCodec(json, names);

    private final RideStatusChangedEvent accepted = new RideStatusChangedEvent(UUID.randomUUID(), RideStatus.MATCHING,
            RideStatus.DRIVER_ASSIGNED, 4, UUID.randomUUID(), UUID.randomUUID(), null, ActorType.DRIVER, null, NOW);

    @Test
    void envelopeCarriesMetadataAndTheEventRoundTrips() {
        UUID eventId = UUID.randomUUID();
        String encoded = codec.encode(eventId, accepted, NOW, "4bf92f3577b34da6");

        JsonNode envelope = json.readTree(encoded);
        assertThat(envelope.get("eventId").asString()).isEqualTo(eventId.toString());
        assertThat(envelope.get("eventType").asString()).isEqualTo("ride.accepted");
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(EventCodec.SCHEMA_VERSION);
        assertThat(envelope.get("aggregateId").asString()).isEqualTo(accepted.rideId().toString());
        assertThat(envelope.get("aggregateVersion").asLong()).isEqualTo(4);

        ReceivedEvent<?> received = codec.decode(PREFIX + "ride.accepted", encoded);
        assertThat(received.eventId()).isEqualTo(eventId);
        assertThat(received.topic()).isEqualTo(EventTopic.RIDE_ACCEPTED);
        assertThat(received.traceId()).isEqualTo("4bf92f3577b34da6");
        assertThat(received.payload()).isEqualTo(accepted);
    }

    @Test
    void nestedValuesSurviveTheRoundTrip() {
        DriverLocationUpdatedEvent location = new DriverLocationUpdatedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), RideStatus.IN_PROGRESS, new GeoPoint(17.4435, 78.3772), 90, 8.5, 5.0, NOW,
                NOW.plusMillis(40), new EtaDestination(EtaResponse.Target.DROPOFF, new GeoPoint(17.4239, 78.4738)));

        String encoded = codec.encode(UUID.randomUUID(), location, NOW, null);

        assertThat(codec.decode(PREFIX + "driver.location.updated", encoded).payload()).isEqualTo(location);
    }

    @Test
    void fieldsAddedByANewerProducerAreIgnored() {
        ObjectNode envelope = (ObjectNode) json.readTree(codec.encode(UUID.randomUUID(), accepted, NOW, null));
        ((ObjectNode) envelope.get("payload")).put("addedLater", "value");
        envelope.put("alsoAddedLater", 1);

        assertThat(codec.decode(PREFIX + "ride.accepted", json.writeValueAsString(envelope)).payload()).isEqualTo(accepted);
    }

    @Test
    void recordsThatCannotBeEventsForTheirTopicAreRejected() {
        String valid = codec.encode(UUID.randomUUID(), accepted, NOW, null);
        ObjectNode futureVersion = (ObjectNode) json.readTree(valid);
        futureVersion.put("schemaVersion", EventCodec.SCHEMA_VERSION + 1);
        ObjectNode noPayload = (ObjectNode) json.readTree(valid);
        noPayload.remove("payload");

        assertThatThrownBy(() -> codec.decode(PREFIX + "ride.accepted", "not json"))
                .isInstanceOf(EventDecodingException.class);
        assertThatThrownBy(() -> codec.decode(PREFIX + "ride.accepted", json.writeValueAsString(futureVersion)))
                .isInstanceOf(EventDecodingException.class).hasMessageContaining("schema version");
        assertThatThrownBy(() -> codec.decode(PREFIX + "ride.accepted", json.writeValueAsString(noPayload)))
                .isInstanceOf(EventDecodingException.class);
        assertThatThrownBy(() -> codec.decode(PREFIX + "ride.completed", valid))
                .isInstanceOf(EventDecodingException.class).hasMessageContaining("ride.accepted");
        assertThatThrownBy(() -> codec.decode("other.ride.accepted", valid))
                .isInstanceOf(EventDecodingException.class).hasMessageContaining("unknown topic");
    }
}
