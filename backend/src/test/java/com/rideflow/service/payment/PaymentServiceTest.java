package com.rideflow.service.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rideflow.entity.FareKind;
import com.rideflow.entity.PaymentMethod;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import com.rideflow.repository.FareBreakdownRepository;
import com.rideflow.repository.PaymentRepository;
import com.rideflow.repository.RideRepository;
import com.rideflow.service.event.DomainEventPublisher;
import com.rideflow.service.event.ProcessedEvents;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The cases where a ride must not be charged, or cannot be. The successful settlement, with the real split and
 * gateways, runs against PostgreSQL and Kafka in KafkaEventFlowIT and RideWorkflowIT.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID RIDE_ID = UUID.randomUUID();

    @Mock
    private RideRepository rides;
    @Mock
    private FareBreakdownRepository fareBreakdowns;
    @Mock
    private PaymentRepository payments;
    @Mock
    private ProcessedEvents processedEvents;
    @Mock
    private DomainEventPublisher events;
    @Mock
    private FareSplitter splitter;
    @Mock
    private PaymentGateway cash;
    @Mock
    private PaymentGateway card;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        when(cash.method()).thenReturn(PaymentMethod.CASH);
        when(card.method()).thenReturn(PaymentMethod.CARD);
        service = new PaymentService(rides, fareBreakdowns, payments, splitter, List.of(cash, card),
                processedEvents, events, Clock.systemUTC());
    }

    private void assertNothingCharged() {
        verify(cash, never()).charge(any());
        verify(card, never()).charge(any());
        verify(payments, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void aRedeliveredEventIsSkipped() {
        when(processedEvents.firstDelivery("payments", EVENT_ID)).thenReturn(false);

        service.settleCompletedRide(EVENT_ID, RIDE_ID);

        verifyNoInteractions(rides);
        assertNothingCharged();
    }

    @Test
    void aRideThatIsAlreadyPaidIsNotChargedAgainForAnotherEvent() {
        when(processedEvents.firstDelivery("payments", EVENT_ID)).thenReturn(true);
        when(payments.existsByRideId(RIDE_ID)).thenReturn(true);

        service.settleCompletedRide(EVENT_ID, RIDE_ID);

        verifyNoInteractions(rides);
        assertNothingCharged();
    }

    @Test
    void anUnknownRideIsSkipped() {
        when(processedEvents.firstDelivery("payments", EVENT_ID)).thenReturn(true);
        when(rides.findById(RIDE_ID)).thenReturn(Optional.empty());

        service.settleCompletedRide(EVENT_ID, RIDE_ID);

        assertNothingCharged();
    }

    @Test
    void aRideThatIsNotCompletedIsNotCharged() {
        Ride ride = mock(Ride.class);
        when(ride.getStatus()).thenReturn(RideStatus.CANCELLED);
        when(processedEvents.firstDelivery("payments", EVENT_ID)).thenReturn(true);
        when(rides.findById(RIDE_ID)).thenReturn(Optional.of(ride));

        service.settleCompletedRide(EVENT_ID, RIDE_ID);

        assertNothingCharged();
    }

    @Test
    void aCompletedRideWithoutAFinalFareFailsSoTheConsumerRetriesAndThenDeadLetters() {
        Ride ride = mock(Ride.class);
        when(ride.getStatus()).thenReturn(RideStatus.COMPLETED);
        when(processedEvents.firstDelivery("payments", EVENT_ID)).thenReturn(true);
        when(rides.findById(RIDE_ID)).thenReturn(Optional.of(ride));
        when(fareBreakdowns.findByRideIdAndKind(RIDE_ID, FareKind.FINAL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.settleCompletedRide(EVENT_ID, RIDE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no final fare");
        assertNothingCharged();
    }

    @Test
    void startupFailsWhenAPaymentMethodHasNoGateway() {
        assertThatThrownBy(() -> new PaymentService(rides, fareBreakdowns, payments, splitter, List.of(cash),
                processedEvents, events, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No payment gateway for CARD");
    }
}
