package com.rideflow.service.ride;

import com.rideflow.entity.OfferStatus;
import com.rideflow.monitoring.RideMetrics;
import com.rideflow.repository.RideOfferRepository;
import com.rideflow.service.event.DomainEventPublisher;
import com.rideflow.service.ride.event.RideOffersWithdrawnEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels a ride's pending offers and announces which drivers lost one, so their apps drop it at once
 * instead of waiting for the offer to expire. The caller must hold the ride row lock (D19), which also
 * stops a matching round from adding offers between the lookup and the update.
 */
@Component
public class RideOfferWithdrawal {

    private final RideOfferRepository offers;
    private final DomainEventPublisher events;
    private final RideMetrics metrics;

    public RideOfferWithdrawal(RideOfferRepository offers, DomainEventPublisher events, RideMetrics metrics) {
        this.offers = offers;
        this.events = events;
        this.metrics = metrics;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void withdrawPending(UUID rideId, Instant now) {
        List<UUID> driverIds = offers.findDriverIdsByRideIdAndStatus(rideId, OfferStatus.PENDING);
        if (driverIds.isEmpty()) {
            return;
        }
        metrics.offersClosed(OfferStatus.CANCELLED, offers.cancelPendingForRide(rideId, now));
        events.publish(new RideOffersWithdrawnEvent(rideId, driverIds));
    }
}
