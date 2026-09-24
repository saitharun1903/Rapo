package com.rideflow.service.driver;

import com.rideflow.cache.DriverPositionCache;
import com.rideflow.cache.DriverPositionCache.StoreResult;
import com.rideflow.repository.DriverLocationRepository;
import com.rideflow.repository.DriverPosition;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A driver's latest known position: the live copy in Redis, or PostgreSQL (which receives positions in batches,
 * so it can trail by a moment) when Redis has none. Everything that needs "where is the driver now" reads
 * through here: the pickup geofence, the tracking snapshot, the final track point and presence checks.
 */
@Component
public class DriverPositions {

    private final DriverPositionCache cache;
    private final DriverLocationRepository driverLocations;

    public DriverPositions(DriverPositionCache cache, DriverLocationRepository driverLocations) {
        this.cache = cache;
        this.driverLocations = driverLocations;
    }

    /** @return {@code false} if a newer position is already stored (the report arrived out of order) */
    public boolean record(UUID driverId, DriverPosition position) {
        return cache.store(driverId, position) != StoreResult.OUTDATED;
    }

    public Optional<DriverPosition> latest(UUID driverId) {
        return cache.find(driverId).or(() -> driverLocations.find(driverId));
    }
}
