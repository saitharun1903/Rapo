package com.rideflow.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.rideflow.entity.ActorType;
import com.rideflow.entity.NotificationType;
import com.rideflow.entity.PaymentMethod;
import com.rideflow.entity.PaymentStatus;
import com.rideflow.entity.RideStatus;
import com.rideflow.service.notification.NotificationComposer.Draft;
import com.rideflow.service.notification.event.NotificationRequestedEvent;
import com.rideflow.service.payment.event.PaymentCreatedEvent;
import com.rideflow.service.ride.event.RideStatusChangedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationComposerTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final UUID RIDE = UUID.randomUUID();
    private static final UUID PASSENGER = UUID.randomUUID();
    private static final UUID DRIVER = UUID.randomUUID();

    private final NotificationComposer composer = new NotificationComposer();

    private static RideStatusChangedEvent change(RideStatus from, RideStatus to, UUID driver, UUID released,
                                                 ActorType actor) {
        return new RideStatusChangedEvent(RIDE, from, to, 3, PASSENGER, driver, released, actor, null, NOW);
    }

    @Test
    void passengerFollowsTheirRideAndNothingIsSentForInternalSteps() {
        assertThat(composer.forRideStatus(change(RideStatus.MATCHING, RideStatus.DRIVER_ASSIGNED, DRIVER, null,
                ActorType.DRIVER)))
                .singleElement().satisfies(draft -> {
                    assertThat(draft.userId()).isEqualTo(PASSENGER);
                    assertThat(draft.type()).isEqualTo(NotificationType.DRIVER_ACCEPTED);
                    assertThat(draft.rideId()).isEqualTo(RIDE);
                });
        assertThat(composer.forRideStatus(change(null, RideStatus.REQUESTED, null, null, ActorType.PASSENGER))).isEmpty();
        assertThat(composer.forRideStatus(change(RideStatus.REQUESTED, RideStatus.MATCHING, null, null, ActorType.SYSTEM)))
                .isEmpty();
    }

    @Test
    void aDriverBackingOutTellsThePassengerASearchIsUnderway() {
        List<Draft> drafts = composer.forRideStatus(change(RideStatus.DRIVER_ASSIGNED, RideStatus.MATCHING, null, DRIVER,
                ActorType.DRIVER));

        assertThat(drafts).extracting(Draft::userId, Draft::type)
                .containsExactly(tuple(PASSENGER, NotificationType.DRIVER_REASSIGNING));
    }

    @Test
    void cancellationsNotifyTheOtherSideOnly() {
        assertThat(composer.forRideStatus(change(RideStatus.DRIVER_ARRIVING, RideStatus.CANCELLED, DRIVER, null,
                ActorType.PASSENGER))).extracting(Draft::userId).containsExactly(DRIVER);
        assertThat(composer.forRideStatus(change(RideStatus.DRIVER_ARRIVED, RideStatus.CANCELLED, DRIVER, null,
                ActorType.DRIVER))).extracting(Draft::userId).containsExactly(PASSENGER);
        // Before a driver was assigned there is nobody else to tell.
        assertThat(composer.forRideStatus(change(RideStatus.MATCHING, RideStatus.CANCELLED, null, null,
                ActorType.PASSENGER))).isEmpty();
    }

    @Test
    void paymentsNotifyBothSidesAndLabelSandboxCards() {
        PaymentCreatedEvent payment = new PaymentCreatedEvent(UUID.randomUUID(), RIDE, PASSENGER, DRIVER,
                new BigDecimal("245.00"), "INR", PaymentMethod.CARD, PaymentStatus.CAPTURED, new BigDecimal("196.00"), NOW);

        List<Draft> drafts = composer.forPayment(payment);

        assertThat(drafts).extracting(Draft::userId).containsExactly(PASSENGER, DRIVER);
        assertThat(drafts.get(0).body()).contains("245.00 INR").contains("sandbox");
        assertThat(drafts.get(1).type()).isEqualTo(NotificationType.EARNINGS_RECORDED);
        assertThat(drafts.get(1).body()).contains("196.00 INR");
    }

    @Test
    void verificationDecisionsAreComposedForTheDriver() {
        Draft rejected = composer.forRequest(new NotificationRequestedEvent(DRIVER, NotificationType.DRIVER_REJECTED,
                null, "Licence photo unreadable"));

        assertThat(rejected.userId()).isEqualTo(DRIVER);
        assertThat(rejected.body()).endsWith("Licence photo unreadable");
    }
}
