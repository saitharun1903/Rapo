package com.rideflow.service.notification;

import com.rideflow.entity.ActorType;
import com.rideflow.entity.NotificationType;
import com.rideflow.entity.PaymentMethod;
import com.rideflow.entity.RideStatus;
import com.rideflow.service.notification.event.NotificationRequestedEvent;
import com.rideflow.service.payment.event.PaymentCreatedEvent;
import com.rideflow.service.ride.event.RideStatusChangedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Decides who is notified about an event and with what text. Pure: no I/O, so every rule is unit-tested.
 * Texts never include personal data of the other party; the ride view shows names to those allowed to see them.
 */
@Component
public class NotificationComposer {

    /** A notification to store and push. */
    public record Draft(UUID userId, NotificationType type, String title, String body, UUID rideId) {
    }

    public List<Draft> forRideStatus(RideStatusChangedEvent event) {
        UUID rideId = event.rideId();
        UUID passenger = event.passengerId();
        return switch (event.to()) {
            case DRIVER_ASSIGNED -> List.of(new Draft(passenger, NotificationType.DRIVER_ACCEPTED,
                    "Driver found", "A driver accepted your ride.", rideId));
            case DRIVER_ARRIVING -> List.of(new Draft(passenger, NotificationType.DRIVER_ARRIVING,
                    "Driver on the way", "Your driver is heading to the pickup point.", rideId));
            case DRIVER_ARRIVED -> List.of(new Draft(passenger, NotificationType.DRIVER_ARRIVED,
                    "Your driver has arrived", "Your driver is waiting at the pickup point.", rideId));
            case IN_PROGRESS -> List.of(new Draft(passenger, NotificationType.TRIP_STARTED,
                    "Trip started", "Your trip has started.", rideId));
            case COMPLETED -> List.of(new Draft(passenger, NotificationType.TRIP_COMPLETED,
                    "Trip completed", "You have arrived. You can now rate your driver.", rideId));
            case EXPIRED -> List.of(new Draft(passenger, NotificationType.RIDE_EXPIRED,
                    "No driver available", "No driver accepted your ride. Please try again in a few minutes.", rideId));
            case MATCHING -> event.releasedDriverId() == null ? List.of() : List.of(new Draft(passenger,
                    NotificationType.DRIVER_REASSIGNING, "Finding you another driver",
                    "Your driver could not make it. We are looking for another one.", rideId));
            case CANCELLED -> cancelled(event);
            case REQUESTED -> List.of();
        };
    }

    public List<Draft> forPayment(PaymentCreatedEvent event) {
        String amount = event.amount().toPlainString() + " " + event.currency();
        String how = event.method() == PaymentMethod.CASH ? "in cash" : "by card (sandbox, no real charge)";
        List<Draft> drafts = new ArrayList<>(2);
        drafts.add(new Draft(event.passengerId(), NotificationType.PAYMENT_RECEIVED, "Payment received",
                "Paid " + amount + " " + how + ".", event.rideId()));
        if (event.driverId() != null) {
            drafts.add(new Draft(event.driverId(), NotificationType.EARNINGS_RECORDED, "Trip earnings",
                    "You earned " + event.driverEarnings().toPlainString() + " " + event.currency()
                            + " for this trip.", event.rideId()));
        }
        return drafts;
    }

    public Draft forRequest(NotificationRequestedEvent event) {
        return switch (event.type()) {
            case DRIVER_VERIFIED -> new Draft(event.userId(), event.type(), "You are verified",
                    "Your driver profile was approved. You can go online now.", event.rideId());
            case DRIVER_REJECTED -> new Draft(event.userId(), event.type(), "Profile not approved",
                    event.detail() == null || event.detail().isBlank()
                            ? "Your driver profile was not approved."
                            : "Your driver profile was not approved: " + event.detail(), event.rideId());
            default -> throw new IllegalArgumentException("Notification type " + event.type()
                    + " is created from ride and payment events, not requested");
        };
    }

    /** The other side learns about a cancellation; for system or admin cancellations, both do. */
    private static List<Draft> cancelled(RideStatusChangedEvent event) {
        List<Draft> drafts = new ArrayList<>(2);
        boolean notifyPassenger = event.actor() != ActorType.PASSENGER;
        boolean notifyDriver = event.actor() != ActorType.DRIVER && event.driverId() != null
                && event.from() != RideStatus.MATCHING && event.from() != RideStatus.REQUESTED;
        String body = switch (event.actor()) {
            case PASSENGER -> "The passenger cancelled the ride.";
            case DRIVER -> "Your driver cancelled the ride.";
            case SYSTEM, ADMIN -> "The ride was cancelled.";
        };
        if (notifyPassenger) {
            drafts.add(new Draft(event.passengerId(), NotificationType.RIDE_CANCELLED, "Ride cancelled", body,
                    event.rideId()));
        }
        if (notifyDriver) {
            drafts.add(new Draft(event.driverId(), NotificationType.RIDE_CANCELLED, "Ride cancelled", body,
                    event.rideId()));
        }
        return drafts;
    }
}
