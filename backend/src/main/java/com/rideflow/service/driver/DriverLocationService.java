package com.rideflow.service.driver;

import com.rideflow.config.RideProperties;
import com.rideflow.dto.driver.LocationUpdateRequest;
import com.rideflow.entity.Driver;
import com.rideflow.entity.DriverAvailability;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import com.rideflow.exception.ResourceNotFoundException;
import com.rideflow.exception.RideFlowException;
import com.rideflow.repository.DriverLocationRepository;
import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.RideRepository;
import com.rideflow.repository.RideTrackPointRepository;
import com.rideflow.service.driver.event.DriverLocationUpdatedEvent;
import com.rideflow.service.event.DomainEventPublisher;
import com.rideflow.service.ride.EtaDestination;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingests driver GPS reports, from the WebSocket stream or the REST fallback. Current design (Phase 4): one
 * conditional upsert per report, a sampled track point while a trip is in progress, and an after-commit
 * push to the passenger while a driver is engaged in a ride. Phases 5 and 6 move the hot path to Redis and a
 * batched Kafka consumer (docs/architecture.md section 8); the validation rules here stay the same.
 */
@Service
public class DriverLocationService {

    private final DriverRepository drivers;
    private final DriverLocationRepository driverLocations;
    private final RideRepository rides;
    private final RideTrackPointRepository trackPoints;
    private final DomainEventPublisher events;
    private final RideProperties.Location rules;
    private final Clock clock;

    public DriverLocationService(DriverRepository drivers, DriverLocationRepository driverLocations,
                                 RideRepository rides, RideTrackPointRepository trackPoints,
                                 DomainEventPublisher events, RideProperties properties, Clock clock) {
        this.drivers = drivers;
        this.driverLocations = driverLocations;
        this.rides = rides;
        this.trackPoints = trackPoints;
        this.events = events;
        this.rules = properties.location();
        this.clock = clock;
    }

    @Transactional
    public void report(UUID driverId, LocationUpdateRequest update) {
        Instant now = clock.instant();
        if (update.recordedAt().isAfter(now.plus(rules.maxFutureSkew()))
                || update.recordedAt().isBefore(now.minus(rules.maxAge()))) {
            throw new RideFlowException(ErrorCode.STALE_LOCATION,
                    "Location timestamp is too old or in the future; check the device clock");
        }
        Driver driver = drivers.findById(driverId).orElseThrow(() ->
                new ResourceNotFoundException(ErrorCode.DRIVER_PROFILE_NOT_FOUND, "Submit your driver profile first"));
        if (!driver.isOnline()) {
            throw new InvalidStateException(ErrorCode.DRIVER_OFFLINE, "Go online before sending your location");
        }
        boolean current = driverLocations.upsert(driverId, update.location(), update.headingDeg(), update.speedMps(),
                update.accuracyMeters(), update.recordedAt(), now);
        if (!current || driver.getAvailability() != DriverAvailability.ON_TRIP) {
            return;
        }
        rides.findFirstByDriverIdAndStatusIn(driverId, RideStatus.DRIVER_ENGAGED).ifPresent(ride -> {
            if (ride.getStatus() == RideStatus.IN_PROGRESS) {
                trackPoints.appendIfSampled(ride.getId(), update.location(), update.recordedAt(),
                        rules.trackMinInterval(), rules.trackMinDistanceMeters());
            }
            publishToPassenger(driverId, ride, update);
        });
    }

    private void publishToPassenger(UUID driverId, Ride ride, LocationUpdateRequest update) {
        events.publish(new DriverLocationUpdatedEvent(driverId, ride.getId(), ride.getPassengerId(),
                update.location(), update.headingDeg(), update.speedMps(), update.recordedAt(),
                EtaDestination.of(ride).orElse(null)));
    }
}
