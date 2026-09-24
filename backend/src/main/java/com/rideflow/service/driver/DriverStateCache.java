package com.rideflow.service.driver;

import com.rideflow.cache.CacheName;
import com.rideflow.cache.JsonCache;
import com.rideflow.cache.RedisKeys;
import com.rideflow.entity.Driver;
import com.rideflow.entity.DriverAvailability;
import com.rideflow.entity.RideStatus;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.ResourceNotFoundException;
import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.RideRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link DriverLiveState} in Redis ({@code driver:{id}:state}), so a location report costs no database read.
 *
 * <p>Consistency: every change to a driver's availability or ride calls {@link #refreshAfterCommit}. The new
 * state is read inside that transaction just before it commits and written after the commit, overwriting the
 * entry. A report that misses the cache loads the state from the database and stores it only if the key is
 * still absent. So a load that raced with a change (it read the old state, the change's write came first)
 * cannot overwrite the newer state. The TTL bounds staleness should a write be lost while Redis is failing.
 */
@Component
public class DriverStateCache {

    private final JsonCache cache;
    private final DriverRepository drivers;
    private final RideRepository rides;

    public DriverStateCache(JsonCache cache, DriverRepository drivers, RideRepository rides) {
        this.cache = cache;
        this.drivers = drivers;
        this.rides = rides;
    }

    /** @throws ResourceNotFoundException if the user has no driver profile */
    public DriverLiveState current(UUID driverId) {
        String key = RedisKeys.driverState(driverId);
        return cache.peek(CacheName.DRIVER_STATE, key, DriverLiveState.class).orElseGet(() -> {
            DriverLiveState state = load(driverId);
            cache.putIfAbsent(CacheName.DRIVER_STATE, key, state);
            return state;
        });
    }

    /**
     * Rewrites the cached state of these drivers once the current transaction commits; {@code null} ids are
     * ignored. Must be called inside the transaction that changes them.
     */
    public void refreshAfterCommit(UUID... driverIds) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (UUID driverId : driverIds) {
            if (driverId != null) {
                ids.add(driverId);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            private final Map<UUID, DriverLiveState> committed = new LinkedHashMap<>();

            @Override
            public void beforeCommit(boolean readOnly) {
                ids.forEach(driverId -> committed.put(driverId, load(driverId)));
            }

            @Override
            public void afterCommit() {
                committed.forEach((driverId, state) ->
                        cache.put(CacheName.DRIVER_STATE, RedisKeys.driverState(driverId), state));
            }
        });
    }

    private DriverLiveState load(UUID driverId) {
        Driver driver = drivers.findById(driverId).orElseThrow(() ->
                new ResourceNotFoundException(ErrorCode.DRIVER_PROFILE_NOT_FOUND, "Submit your driver profile first"));
        if (driver.getAvailability() != DriverAvailability.ON_TRIP) {
            return new DriverLiveState(driver.getAvailability(), null);
        }
        DriverLiveState.ActiveRide activeRide = rides.findFirstByDriverIdAndStatusIn(driverId, RideStatus.DRIVER_ENGAGED)
                .map(ride -> new DriverLiveState.ActiveRide(ride.getId(), ride.getPassengerId(), ride.getStatus(),
                        ride.getPickup(), ride.getDropoff()))
                .orElse(null);
        return new DriverLiveState(driver.getAvailability(), activeRide);
    }
}
