package com.rideflow.service.driver;

import com.rideflow.entity.Driver;
import com.rideflow.entity.DriverAvailability;
import com.rideflow.entity.OfflineReason;
import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.RideOfferRepository;
import com.rideflow.service.driver.event.DriverWentOfflineEvent;
import com.rideflow.service.event.DomainEventPublisher;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Takes silent drivers offline. Socket state is not used: a dropped connection that recovers within the
 * timeout should not cost the driver their place, and REST-only clients have no socket at all.
 */
@Service
public class DriverPresenceService {

    private static final Logger log = LoggerFactory.getLogger(DriverPresenceService.class);

    private final DriverRepository drivers;
    private final DriverPositions positions;
    private final DriverStateCache driverStates;
    private final RideOfferRepository offers;
    private final DomainEventPublisher events;

    public DriverPresenceService(DriverRepository drivers, DriverPositions positions, DriverStateCache driverStates,
                                 RideOfferRepository offers, DomainEventPublisher events) {
        this.drivers = drivers;
        this.positions = positions;
        this.driverStates = driverStates;
        this.offers = offers;
        this.events = events;
    }

    /**
     * Sets the driver OFFLINE if, under the driver row lock, they are still AVAILABLE and still silent.
     * The re-check matters: the driver may have reported a position or accepted a ride since the sweep
     * query ran. Drivers on a trip are never touched: losing GPS mid-trip must not end the trip.
     *
     * @return {@code true} if the driver was taken offline
     */
    @Transactional
    public boolean takeOfflineIfSilent(UUID driverId, Instant silentSince, Instant now) {
        Driver driver = drivers.findByIdForUpdate(driverId).orElse(null);
        if (driver == null || driver.getAvailability() != DriverAvailability.AVAILABLE) {
            return false;
        }
        // The live position (Redis) counts, not only PostgreSQL, which can trail when Kafka is slow or down.
        boolean silent = positions.latest(driverId)
                .map(position -> position.updatedAt().isBefore(silentSince))
                .orElse(true);
        if (!silent) {
            return false;
        }
        driver.goOffline();
        offers.cancelPendingForDriver(driverId, now);
        driverStates.refreshAfterCommit(driverId);
        events.publish(new DriverWentOfflineEvent(driverId, OfflineReason.LOCATION_TIMEOUT, now));
        log.info("Driver {} taken offline: no location update since {}", driverId, silentSince);
        return true;
    }
}
