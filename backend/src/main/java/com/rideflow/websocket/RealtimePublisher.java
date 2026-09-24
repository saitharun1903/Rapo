package com.rideflow.websocket;

import com.rideflow.dto.realtime.DriverLocationMessage;
import com.rideflow.dto.realtime.DriverPresenceMessage;
import com.rideflow.dto.realtime.RideActivityMessage;
import com.rideflow.dto.realtime.RideOfferMessage;
import com.rideflow.dto.ride.EtaResponse;
import com.rideflow.entity.DriverAvailability;
import com.rideflow.service.driver.event.DriverLocationUpdatedEvent;
import com.rideflow.service.driver.event.DriverWentOfflineEvent;
import com.rideflow.service.matching.OfferQueryService;
import com.rideflow.service.ride.LiveEtaService;
import com.rideflow.service.ride.RideQueryService;
import com.rideflow.service.ride.event.RideOffersCreatedEvent;
import com.rideflow.service.ride.event.RideOffersWithdrawnEvent;
import com.rideflow.service.ride.event.RideStatusChangedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns committed domain events into WebSocket pushes (contract: docs/events.md section 2.3).
 *
 * <p>Recipients are always derived from current data at send time: ride updates go to the ride's passenger
 * and its current driver, locations to the passenger of the driver's active ride, offers to the offered
 * driver. Per-user queues are used instead of per-ride topics, so a driver who has been unassigned from a
 * ride stops receiving it immediately (see D20 in docs/architecture.md).
 *
 * <p>Pushes are best effort: a failed push is logged and counted, and never affects the change that caused
 * it. Clients recover on reconnect from the REST snapshot, and they discard ride updates whose
 * {@code version} is not newer than what they show, so out-of-order delivery from the async listeners is
 * harmless.
 */
@Component
public class RealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

    private final SimpMessagingTemplate messaging;
    private final RideQueryService rideQueries;
    private final OfferQueryService offerQueries;
    private final LiveEtaService liveEta;
    private final Counter failures;

    public RealtimePublisher(SimpMessagingTemplate messaging, RideQueryService rideQueries,
                             OfferQueryService offerQueries, LiveEtaService liveEta, MeterRegistry meters) {
        this.messaging = messaging;
        this.rideQueries = rideQueries;
        this.offerQueries = offerQueries;
        this.liveEta = liveEta;
        this.failures = Counter.builder("rideflow.ws.push.failures")
                .description("WebSocket pushes that could not be handed to the broker")
                .register(meters);
    }

    /** Async: loading the ride view needs its own transaction and must not delay the HTTP response. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRideStatusChanged(RideStatusChangedEvent event) {
        send(StompDestinations.ADMIN_ACTIVITY, new RideActivityMessage(event.rideId(), event.from(), event.to(),
                event.actor(), event.rideVersion(), event.occurredAt()));
        rideQueries.findForParticipants(event.rideId()).ifPresent(ride -> {
            if (ride.passenger() != null) {
                sendToUser(ride.passenger().id(), StompDestinations.RIDE_UPDATES, ride);
            }
            if (ride.driver() != null) {
                sendToUser(ride.driver().id(), StompDestinations.RIDE_UPDATES, ride);
            }
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOffersCreated(RideOffersCreatedEvent event) {
        for (UUID driverId : event.driverIds()) {
            offerQueries.openOffer(driverId, event.rideId())
                    .ifPresent(offer -> sendToUser(driverId, StompDestinations.RIDE_OFFERS, RideOfferMessage.offer(offer)));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOffersWithdrawn(RideOffersWithdrawnEvent event) {
        RideOfferMessage message = RideOfferMessage.withdrawn(event.rideId());
        event.driverIds().forEach(driverId -> sendToUser(driverId, StompDestinations.RIDE_OFFERS, message));
    }

    /** Carries the cached ETA; when there is none, a background refresh fills it for the next update. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDriverLocationUpdated(DriverLocationUpdatedEvent event) {
        EtaResponse eta = null;
        if (event.destination() != null) {
            eta = liveEta.cached(event.rideId(), event.destination()).orElse(null);
            if (eta == null) {
                liveEta.refreshInBackground(event.rideId(), event.location(), event.destination());
            }
        }
        sendToUser(event.passengerId(), StompDestinations.RIDE_LOCATION, new DriverLocationMessage(event.rideId(),
                event.location(), event.headingDeg(), event.speedMps(), event.recordedAt(), eta));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDriverWentOffline(DriverWentOfflineEvent event) {
        sendToUser(event.driverId(), StompDestinations.PRESENCE,
                new DriverPresenceMessage(DriverAvailability.OFFLINE, event.reason(), event.occurredAt()));
    }

    private void sendToUser(UUID userId, String destination, Object payload) {
        try {
            messaging.convertAndSendToUser(userId.toString(), destination, payload);
        } catch (MessagingException ex) {
            failures.increment();
            log.warn("Push to {} for user {} failed: {}", destination, userId, ex.getMessage());
        }
    }

    private void send(String destination, Object payload) {
        try {
            messaging.convertAndSend(destination, payload);
        } catch (MessagingException ex) {
            failures.increment();
            log.warn("Push to {} failed: {}", destination, ex.getMessage());
        }
    }
}
