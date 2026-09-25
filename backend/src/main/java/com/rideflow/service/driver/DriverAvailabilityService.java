package com.rideflow.service.driver;

import com.rideflow.dto.driver.DriverResponse;
import com.rideflow.entity.Driver;
import com.rideflow.entity.OfflineReason;
import com.rideflow.entity.Vehicle;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import com.rideflow.exception.ResourceNotFoundException;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.mapper.DriverMapper;
import com.rideflow.repository.DriverLocationRepository;
import com.rideflow.repository.DriverPosition;
import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.VehicleRepository;
import com.rideflow.service.driver.event.DriverWentOfflineEvent;
import com.rideflow.service.event.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Online/offline state. Going online requires verification, an active vehicle and a current position. */
@Service
@Transactional
public class DriverAvailabilityService {

    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final DriverLocationRepository driverLocations;
    private final DriverOfferWithdrawal offerWithdrawal;
    private final DriverMapper driverMapper;
    private final DriverPositions positions;
    private final DriverStateCache driverStates;
    private final DomainEventPublisher events;
    private final Clock clock;

    public DriverAvailabilityService(DriverRepository drivers, VehicleRepository vehicles,
                                     DriverLocationRepository driverLocations, DriverOfferWithdrawal offerWithdrawal,
                                     DriverMapper driverMapper, DriverPositions positions,
                                     DriverStateCache driverStates, DomainEventPublisher events, Clock clock) {
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.driverLocations = driverLocations;
        this.offerWithdrawal = offerWithdrawal;
        this.driverMapper = driverMapper;
        this.positions = positions;
        this.driverStates = driverStates;
        this.events = events;
        this.clock = clock;
    }

    public DriverResponse goOnline(UUID driverId, GeoPoint location) {
        Driver driver = load(driverId);
        if (!driver.getUser().isActive()) {
            throw new InvalidStateException(ErrorCode.ACCOUNT_SUSPENDED, "Suspended accounts cannot go online");
        }
        Vehicle vehicle = vehicles.findByDriverIdAndActiveTrue(driverId).orElseThrow(() ->
                new InvalidStateException(ErrorCode.NO_ACTIVE_VEHICLE, "Register an active vehicle before going online"));
        driver.goOnline();
        Instant now = clock.instant();
        // Written to PostgreSQL at once (not through the batch consumer) so matching can find the driver now.
        driverLocations.upsert(driverId, location, null, null, null, now, now);
        positions.record(driverId, new DriverPosition(location, null, now, now));
        driverStates.refreshAfterCommit(driverId);
        return driverMapper.toResponse(driver, vehicle);
    }

    /** Going offline also withdraws any pending offer so the ride is re-offered to someone else. */
    public DriverResponse goOffline(UUID driverId) {
        Driver driver = load(driverId);
        driver.goOffline();
        offerWithdrawal.withdrawPending(driverId, clock.instant());
        driverStates.refreshAfterCommit(driverId);
        return driverMapper.toResponse(driver, vehicles.findByDriverIdAndActiveTrue(driverId).orElse(null));
    }

    /** Used when an admin suspends the account. Refused while the driver is on a trip. */
    public void forceOffline(UUID driverId) {
        drivers.findById(driverId).ifPresent(driver -> {
            boolean wasOnline = driver.isOnline();
            Instant now = clock.instant();
            driver.goOffline();
            offerWithdrawal.withdrawPending(driverId, now);
            driverStates.refreshAfterCommit(driverId);
            if (wasOnline) {
                events.publish(new DriverWentOfflineEvent(driverId, OfflineReason.ACCOUNT_SUSPENDED, now));
            }
        });
    }

    private Driver load(UUID driverId) {
        return drivers.findWithUserById(driverId).orElseThrow(() ->
                new ResourceNotFoundException(ErrorCode.DRIVER_PROFILE_NOT_FOUND, "Submit your driver profile first"));
    }
}
