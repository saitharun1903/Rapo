package com.rideflow.service.driver;

import com.rideflow.config.RealtimeProperties;
import com.rideflow.repository.DriverLocationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically takes AVAILABLE drivers with no recent location offline, so supply counts (surge) and the
 * nearby-cars map only show drivers who are really there. Matching already ignores positions older than
 * the freshness window; this makes the state explicit and tells the driver. Safe on every instance: each
 * driver is handled under its row lock.
 */
@Component
public class DriverPresenceSweeper {

    private static final Logger log = LoggerFactory.getLogger(DriverPresenceSweeper.class);

    private final DriverLocationRepository driverLocations;
    private final DriverPresenceService presence;
    private final RealtimeProperties.Presence properties;
    private final Clock clock;

    public DriverPresenceSweeper(DriverLocationRepository driverLocations, DriverPresenceService presence,
                                 RealtimeProperties properties, Clock clock) {
        this.driverLocations = driverLocations;
        this.presence = presence;
        this.properties = properties.presence();
        this.clock = clock;
    }

    /** Returns how many drivers were taken offline. */
    @Scheduled(fixedDelayString = "${rideflow.realtime.presence.sweep-interval}")
    public int sweep() {
        Instant now = clock.instant();
        Instant silentSince = now.minus(properties.timeout());
        int takenOffline = 0;
        for (UUID driverId : driverLocations.findSilentAvailableDrivers(silentSince, properties.sweepBatchSize())) {
            try {
                if (presence.takeOfflineIfSilent(driverId, silentSince, now)) {
                    takenOffline++;
                }
            } catch (RuntimeException ex) {
                // Isolate failures per driver: one failure must not keep the others online.
                log.error("Presence check failed for driver {}; will retry on the next sweep", driverId, ex);
            }
        }
        return takenOffline;
    }
}
