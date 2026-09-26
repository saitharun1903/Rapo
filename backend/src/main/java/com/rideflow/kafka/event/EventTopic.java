package com.rideflow.kafka.event;

import com.rideflow.entity.RideStatus;
import com.rideflow.service.chat.event.RideMessageSentEvent;
import com.rideflow.service.driver.event.DriverLocationUpdatedEvent;
import com.rideflow.service.driver.event.DriverWentOfflineEvent;
import com.rideflow.service.event.DomainEvent;
import com.rideflow.service.notification.event.NotificationCreatedEvent;
import com.rideflow.service.notification.event.NotificationRequestedEvent;
import com.rideflow.service.payment.event.PaymentCreatedEvent;
import com.rideflow.service.ride.event.MatchingRoundRequestedEvent;
import com.rideflow.service.ride.event.RideOffersCreatedEvent;
import com.rideflow.service.ride.event.RideOffersWithdrawnEvent;
import com.rideflow.service.ride.event.RideStatusChangedEvent;
import java.util.EnumSet;
import java.util.Set;

/**
 * Every Kafka topic, the payload it carries and how it is produced (catalogue: docs/events.md section 1.2).
 * The base name is also the envelope's {@code eventType}; the deployed topic name adds
 * {@code rideflow.kafka.prefix} (see {@link KafkaNames}).
 *
 * <p>Ride status changes have one topic per target status, so a consumer subscribes only to the transitions
 * it cares about (payments to {@code ride.completed}, for example) instead of filtering every change.
 */
public enum EventTopic {
    RIDE_REQUESTED("ride.requested", RideStatusChangedEvent.class, Retention.LIFECYCLE, Delivery.OUTBOX),
    RIDE_MATCHING("ride.matching", RideStatusChangedEvent.class, Retention.LIFECYCLE, Delivery.OUTBOX),
    RIDE_ACCEPTED("ride.accepted", RideStatusChangedEvent.class, Retention.LIFECYCLE, Delivery.OUTBOX),
    RIDE_DRIVER_ARRIVING("ride.driver.arriving", RideStatusChangedEvent.class, Retention.LIFECYCLE, Delivery.OUTBOX),
    RIDE_DRIVER_ARRIVED("ride.driver.arrived", RideStatusChangedEvent.class, Retention.LIFECYCLE, Delivery.OUTBOX),
    RIDE_STARTED("ride.started", RideStatusChangedEvent.class, Retention.LIFECYCLE, Delivery.OUTBOX),
    RIDE_COMPLETED("ride.completed", RideStatusChangedEvent.class, Retention.LIFECYCLE, Delivery.OUTBOX),
    RIDE_CANCELLED("ride.cancelled", RideStatusChangedEvent.class, Retention.LIFECYCLE, Delivery.OUTBOX),
    RIDE_EXPIRED("ride.expired", RideStatusChangedEvent.class, Retention.LIFECYCLE, Delivery.OUTBOX),
    RIDE_DISPATCH_REQUESTED("ride.dispatch.requested", MatchingRoundRequestedEvent.class, Retention.LIFECYCLE,
            Delivery.OUTBOX),
    RIDE_DRIVER_ASSIGNED("ride.driver.assigned", RideOffersCreatedEvent.class, Retention.LIFECYCLE, Delivery.OUTBOX),
    RIDE_OFFERS_WITHDRAWN("ride.offers.withdrawn", RideOffersWithdrawnEvent.class, Retention.LIFECYCLE,
            Delivery.OUTBOX),
    DRIVER_LOCATION_UPDATED("driver.location.updated", DriverLocationUpdatedEvent.class, Retention.LOCATION,
            Delivery.DIRECT),
    DRIVER_WENT_OFFLINE("driver.offline", DriverWentOfflineEvent.class, Retention.LIFECYCLE, Delivery.OUTBOX),
    PAYMENT_CREATED("payment.created", PaymentCreatedEvent.class, Retention.LIFECYCLE, Delivery.OUTBOX),
    NOTIFICATION_REQUESTED("notification.requested", NotificationRequestedEvent.class, Retention.NOTIFICATION,
            Delivery.OUTBOX),
    NOTIFICATION_CREATED("notification.created", NotificationCreatedEvent.class, Retention.NOTIFICATION,
            Delivery.OUTBOX),
    RIDE_MESSAGE_SENT("ride.message.sent", RideMessageSentEvent.class, Retention.NOTIFICATION, Delivery.OUTBOX);

    /** Topics carrying {@link RideStatusChangedEvent}. */
    public static final Set<EventTopic> RIDE_STATUS_TOPICS = EnumSet.of(RIDE_REQUESTED, RIDE_MATCHING, RIDE_ACCEPTED,
            RIDE_DRIVER_ARRIVING, RIDE_DRIVER_ARRIVED, RIDE_STARTED, RIDE_COMPLETED, RIDE_CANCELLED, RIDE_EXPIRED);

    /** Which configured retention applies ({@code rideflow.kafka.retention}). */
    public enum Retention {
        LIFECYCLE, LOCATION, NOTIFICATION
    }

    /**
     * {@code OUTBOX}: written in the producing transaction and relayed, so delivered iff it committed.
     * {@code DIRECT}: sent straight to Kafka, at most once; only for data that the next record supersedes.
     */
    public enum Delivery {
        OUTBOX, DIRECT
    }

    private final String baseName;
    private final Class<? extends DomainEvent> payloadType;
    private final Retention retention;
    private final Delivery delivery;

    EventTopic(String baseName, Class<? extends DomainEvent> payloadType, Retention retention, Delivery delivery) {
        this.baseName = baseName;
        this.payloadType = payloadType;
        this.retention = retention;
        this.delivery = delivery;
    }

    /** The topic an event is published to. */
    public static EventTopic of(DomainEvent event) {
        return switch (event) {
            case RideStatusChangedEvent change -> forStatus(change.to());
            case MatchingRoundRequestedEvent ignored -> RIDE_DISPATCH_REQUESTED;
            case RideOffersCreatedEvent ignored -> RIDE_DRIVER_ASSIGNED;
            case RideOffersWithdrawnEvent ignored -> RIDE_OFFERS_WITHDRAWN;
            case DriverLocationUpdatedEvent ignored -> DRIVER_LOCATION_UPDATED;
            case DriverWentOfflineEvent ignored -> DRIVER_WENT_OFFLINE;
            case PaymentCreatedEvent ignored -> PAYMENT_CREATED;
            case NotificationRequestedEvent ignored -> NOTIFICATION_REQUESTED;
            case NotificationCreatedEvent ignored -> NOTIFICATION_CREATED;
            case RideMessageSentEvent ignored -> RIDE_MESSAGE_SENT;
            default -> throw new IllegalArgumentException("No topic for " + event.getClass().getName());
        };
    }

    /** Exhaustive over {@link RideStatus}: a new status does not compile until it has a topic. */
    static EventTopic forStatus(RideStatus status) {
        return switch (status) {
            case REQUESTED -> RIDE_REQUESTED;
            case MATCHING -> RIDE_MATCHING;
            case DRIVER_ASSIGNED -> RIDE_ACCEPTED;
            case DRIVER_ARRIVING -> RIDE_DRIVER_ARRIVING;
            case DRIVER_ARRIVED -> RIDE_DRIVER_ARRIVED;
            case IN_PROGRESS -> RIDE_STARTED;
            case COMPLETED -> RIDE_COMPLETED;
            case CANCELLED -> RIDE_CANCELLED;
            case EXPIRED -> RIDE_EXPIRED;
        };
    }

    public String baseName() {
        return baseName;
    }

    /** The envelope's {@code eventType}. */
    public String eventType() {
        return baseName;
    }

    public Class<? extends DomainEvent> payloadType() {
        return payloadType;
    }

    public Retention retention() {
        return retention;
    }

    public Delivery delivery() {
        return delivery;
    }
}
