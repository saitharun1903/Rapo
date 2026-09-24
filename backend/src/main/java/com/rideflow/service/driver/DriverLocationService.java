package com.rideflow.service.driver;

import com.rideflow.config.RideProperties;
import com.rideflow.dto.driver.LocationUpdateRequest;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import com.rideflow.exception.RideFlowException;
import com.rideflow.repository.DriverPosition;
import com.rideflow.service.driver.DriverLiveState.ActiveRide;
import com.rideflow.service.driver.event.DriverLocationUpdatedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Ingests driver GPS reports, from the WebSocket stream or the REST fallback (docs/architecture.md section 8).
 * The hot path touches Redis and Kafka only: the driver's state comes from {@link DriverStateCache}, the
 * position is stored in Redis, and the report goes to {@code driver.location.updated}. From there the realtime
 * bridge pushes it to the ride's passenger and a batch consumer writes it to PostgreSQL.
 */
@Service
public class DriverLocationService {

    private final DriverStateCache driverStates;
    private final DriverPositions positions;
    private final LocationStream stream;
    private final RideProperties.Location rules;
    private final Clock clock;

    public DriverLocationService(DriverStateCache driverStates, DriverPositions positions, LocationStream stream,
                                 RideProperties properties, Clock clock) {
        this.driverStates = driverStates;
        this.positions = positions;
        this.stream = stream;
        this.rules = properties.location();
        this.clock = clock;
    }

    public void report(UUID driverId, LocationUpdateRequest update) {
        Instant now = clock.instant();
        if (update.recordedAt().isAfter(now.plus(rules.maxFutureSkew()))
                || update.recordedAt().isBefore(now.minus(rules.maxAge()))) {
            throw new RideFlowException(ErrorCode.STALE_LOCATION,
                    "Location timestamp is too old or in the future; check the device clock");
        }
        DriverLiveState state = driverStates.current(driverId);
        if (!state.isOnline()) {
            throw new InvalidStateException(ErrorCode.DRIVER_OFFLINE, "Go online before sending your location");
        }
        if (!positions.record(driverId, new DriverPosition(update.location(), update.headingDeg(), update.recordedAt(), now))) {
            return;
        }
        ActiveRide ride = state.activeRide();
        stream.publish(new DriverLocationUpdatedEvent(driverId,
                ride == null ? null : ride.rideId(),
                ride == null ? null : ride.passengerId(),
                ride == null ? null : ride.status(),
                update.location(), update.headingDeg(), update.speedMps(), update.accuracyMeters(),
                update.recordedAt(), now,
                ride == null ? null : ride.destination().orElse(null)));
    }
}
