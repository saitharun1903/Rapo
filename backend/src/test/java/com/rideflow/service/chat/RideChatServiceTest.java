package com.rideflow.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rideflow.cache.RateLimitScope;
import com.rideflow.cache.RateLimiter;
import com.rideflow.dto.chat.RideMessageResponse;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideMessage;
import com.rideflow.entity.RideStatus;
import com.rideflow.entity.Role;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import com.rideflow.repository.RideMessageRepository;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.chat.event.RideMessageSentEvent;
import com.rideflow.service.event.DomainEventPublisher;
import com.rideflow.service.ride.RideAccessPolicy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Who may chat and when; the delivery over Kafka and STOMP is covered by RideChatIT. */
@ExtendWith(MockitoExtension.class)
class RideChatServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");
    private static final UUID RIDE_ID = UUID.randomUUID();
    private static final UUID PASSENGER_ID = UUID.randomUUID();
    private static final UUID DRIVER_ID = UUID.randomUUID();
    private static final AuthenticatedUser PASSENGER = new AuthenticatedUser(PASSENGER_ID, Role.PASSENGER);
    private static final AuthenticatedUser DRIVER = new AuthenticatedUser(DRIVER_ID, Role.DRIVER);

    @Mock
    private RideAccessPolicy access;
    @Mock
    private RideMessageRepository messages;
    @Mock
    private DomainEventPublisher events;
    @Mock
    private RateLimiter rateLimiter;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private RideChatService service;
    private Ride ride;

    @BeforeEach
    void setUp() {
        service = new RideChatService(access, messages, events, rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC), meters);
        ride = mock(Ride.class);
        lenient().when(ride.getPassengerId()).thenReturn(PASSENGER_ID);
        lenient().when(ride.getDriverId()).thenReturn(DRIVER_ID);
        lenient().when(ride.isAssignedTo(any())).thenAnswer(call -> DRIVER_ID.equals(call.getArgument(0)));
        lenient().when(access.loadVisible(any(), any())).thenReturn(ride);
        lenient().when(messages.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"DRIVER_ASSIGNED", "DRIVER_ARRIVING", "DRIVER_ARRIVED", "IN_PROGRESS"})
    void participantsCanWriteWhileADriverIsEngaged(RideStatus status) {
        when(ride.getStatus()).thenReturn(status);

        RideMessageResponse sent = service.send(PASSENGER, RIDE_ID, "  At the gate \n");

        assertThat(sent.body()).isEqualTo("At the gate");
        assertThat(sent.senderRole()).isEqualTo(Role.PASSENGER);
        assertThat(sent.sentAt()).isEqualTo(NOW);
        ArgumentCaptor<RideMessageSentEvent> event = ArgumentCaptor.forClass(RideMessageSentEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().passengerId()).isEqualTo(PASSENGER_ID);
        assertThat(event.getValue().driverId()).isEqualTo(DRIVER_ID);
        verify(rateLimiter).acquire(RateLimitScope.CHAT, PASSENGER_ID.toString());
        assertThat(meters.get("rideflow.chat.messages").tag("role", "PASSENGER").counter().count()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"REQUESTED", "MATCHING", "COMPLETED", "CANCELLED", "EXPIRED"})
    void chatIsClosedWithoutAnEngagedDriver(RideStatus status) {
        when(ride.getStatus()).thenReturn(status);

        assertThatThrownBy(() -> service.send(PASSENGER, RIDE_ID, "Hello"))
                .isInstanceOfSatisfying(RideFlowException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CHAT_CLOSED));
        verify(messages, never()).save(any());
        verify(events, never()).publish(any());
    }

    @Test
    void theOpenStatesAreExactlyThoseWithAnEngagedDriver() {
        assertThat(RideChatService.OPEN).isEqualTo(EnumSet.of(RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVING,
                RideStatus.DRIVER_ARRIVED, RideStatus.IN_PROGRESS));
    }

    @Test
    void aDriverWhoIsNotAssignedIsNotAParticipant() {
        AuthenticatedUser offeredDriver = new AuthenticatedUser(UUID.randomUUID(), Role.DRIVER);

        assertThatThrownBy(() -> service.send(offeredDriver, RIDE_ID, "Hello"))
                .isInstanceOfSatisfying(RideFlowException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.RIDE_NOT_FOUND));
        assertThatThrownBy(() -> service.list(offeredDriver, RIDE_ID)).isInstanceOf(RideFlowException.class);
    }

    @Test
    void adminsAreNotParticipants() {
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), Role.ADMIN);

        assertThatThrownBy(() -> service.list(admin, RIDE_ID))
                .isInstanceOfSatisfying(RideFlowException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.RIDE_NOT_FOUND));
    }

    @Test
    void theAssignedDriverSeesOnlyWhatWasSaidSinceTheyAccepted() {
        Instant acceptedAt = NOW.minusSeconds(60);
        when(ride.getAcceptedAt()).thenReturn(acceptedAt);
        RideMessage before = RideMessage.of(RIDE_ID, PASSENGER_ID, Role.PASSENGER, "To the previous driver",
                acceptedAt.minusSeconds(1));
        RideMessage at = RideMessage.of(RIDE_ID, PASSENGER_ID, Role.PASSENGER, "Gate 2", acceptedAt);
        RideMessage after = RideMessage.of(RIDE_ID, DRIVER_ID, Role.DRIVER, "On my way", acceptedAt.plusSeconds(5));
        when(messages.findByRideIdOrderBySentAtAscIdAsc(RIDE_ID)).thenReturn(List.of(before, at, after));

        assertThat(service.list(DRIVER, RIDE_ID)).extracting(RideMessageResponse::body)
                .containsExactly("Gate 2", "On my way");
        assertThat(service.list(PASSENGER, RIDE_ID)).extracting(RideMessageResponse::body)
                .containsExactly("To the previous driver", "Gate 2", "On my way");
    }

    @Test
    void onlyParticipantRolesCanBeSenders() {
        assertThatThrownBy(() -> RideMessage.of(RIDE_ID, UUID.randomUUID(), Role.ADMIN, "Hi", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
