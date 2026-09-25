package com.rideflow.service.driver;

import com.rideflow.entity.OfferStatus;
import com.rideflow.monitoring.RideMetrics;
import com.rideflow.repository.RideOfferRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels the pending offers of a driver who can no longer take them (offline, suspended), so the next
 * matching round offers those rides to someone else.
 */
@Component
public class DriverOfferWithdrawal {

    private final RideOfferRepository offers;
    private final RideMetrics metrics;

    public DriverOfferWithdrawal(RideOfferRepository offers, RideMetrics metrics) {
        this.offers = offers;
        this.metrics = metrics;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void withdrawPending(UUID driverId, Instant now) {
        metrics.offersClosed(OfferStatus.CANCELLED, offers.cancelPendingForDriver(driverId, now));
    }
}
