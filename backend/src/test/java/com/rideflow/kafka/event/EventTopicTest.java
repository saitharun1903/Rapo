package com.rideflow.kafka.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.entity.ActorType;
import com.rideflow.entity.RideStatus;
import com.rideflow.service.event.DomainEvent;
import com.rideflow.service.ride.event.MatchingRoundRequestedEvent;
import com.rideflow.service.ride.event.RideStatusChangedEvent;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EventTopicTest {

    private static RideStatusChangedEvent statusChange(RideStatus to) {
        return new RideStatusChangedEvent(UUID.randomUUID(), null, to, 1, UUID.randomUUID(), null, null,
                ActorType.SYSTEM, null, Instant.parse("2026-09-25T10:00:00Z"));
    }

    @Test
    void everyRideStatusHasItsOwnTopicCarryingStatusChanges() {
        Set<EventTopic> topics = Arrays.stream(RideStatus.values())
                .map(status -> EventTopic.of(statusChange(status)))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(EventTopic.class)));

        assertThat(topics).hasSize(RideStatus.values().length).isEqualTo(EventTopic.RIDE_STATUS_TOPICS);
        assertThat(topics).allSatisfy(topic -> assertThat(topic.payloadType()).isEqualTo(RideStatusChangedEvent.class));
        assertThat(EventTopic.of(statusChange(RideStatus.COMPLETED)).baseName()).isEqualTo("ride.completed");
        assertThat(EventTopic.of(statusChange(RideStatus.DRIVER_ASSIGNED)).baseName()).isEqualTo("ride.accepted");
    }

    @Test
    void eventsRouteToTheTopicDeclaringTheirPayloadType() {
        MatchingRoundRequestedEvent dispatch = new MatchingRoundRequestedEvent(UUID.randomUUID());

        assertThat(EventTopic.of(dispatch)).isEqualTo(EventTopic.RIDE_DISPATCH_REQUESTED);
        assertThat(EventTopic.of(dispatch).payloadType()).isEqualTo(MatchingRoundRequestedEvent.class);
    }

    @Test
    void topicNamesAreUniqueAndOnlyLocationsBypassTheOutbox() {
        assertThat(Arrays.stream(EventTopic.values()).map(EventTopic::baseName).distinct())
                .hasSize(EventTopic.values().length);
        assertThat(Arrays.stream(EventTopic.values()).filter(topic -> topic.delivery() == EventTopic.Delivery.DIRECT))
                .containsExactly(EventTopic.DRIVER_LOCATION_UPDATED);
    }

    @Test
    void unknownEventTypesAreRejected() {
        DomainEvent unknown = UUID::randomUUID;

        assertThatThrownBy(() -> EventTopic.of(unknown)).isInstanceOf(IllegalArgumentException.class);
    }
}
