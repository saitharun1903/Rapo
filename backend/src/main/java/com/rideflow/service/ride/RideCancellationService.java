package com.rideflow.service.ride;

import com.rideflow.config.RideProperties;
import com.rideflow.dto.ride.RideResponse;
import com.rideflow.entity.ActorType;
import com.rideflow.entity.Driver;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import com.rideflow.entity.Role;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.RideOfferRepository;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.ride.event.MatchingRoundRequestedEvent;
import com.rideflow.service.ride.event.RideEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancellation rules differ by who cancels:
 * <ul>
 *   <li>Passenger: any time before the trip starts; the ride ends.</li>
 *   <li>Driver before arrival: the driver is released and the ride goes back to matching (re-dispatch).</li>
 *   <li>Driver after arrival: only once the no-show wait has elapsed; the ride ends.</li>
 * </ul>
 */
@Service
public class RideCancellationService {

    private static final Logger log = LoggerFactory.getLogger(RideCancellationService.class);
    private static final Set<RideStatus> REDISPATCHABLE = EnumSet.of(RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVING);
    private static final String NO_SHOW_REASON = "Passenger did not show up";

    private final RideAccessPolicy access;
    private final RideOfferRepository offers;
    private final DriverRepository drivers;
    private final RideTransitionRecorder recorder;
    private final RideEventPublisher events;
    private final RideViewAssembler views;
    private final RideProperties properties;
    private final Clock clock;

    public RideCancellationService(RideAccessPolicy access, RideOfferRepository offers, DriverRepository drivers,
                                   RideTransitionRecorder recorder, RideEventPublisher events,
                                   RideViewAssembler views, RideProperties properties, Clock clock) {
        this.access = access;
        this.offers = offers;
        this.drivers = drivers;
        this.recorder = recorder;
        this.events = events;
        this.views = views;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public RideResponse cancel(AuthenticatedUser user, UUID rideId, String reason) {
        Ride ride = access.lockOwnedBy(user, rideId);
        Instant now = clock.instant();
        Ride result = user.role() == Role.PASSENGER
                ? cancelByPassenger(ride, user.id(), reason, now)
                : cancelByDriver(ride, user.id(), reason, now);
        return views.toResponse(result);
    }

    private Ride cancelByPassenger(Ride ride, UUID passengerId, String reason, Instant now) {
        UUID assignedDriver = ride.getDriverId();
        Ride.StatusChange change = ride.cancel(ActorType.PASSENGER, reason, now);
        offers.cancelPendingForRide(ride.getId(), now);
        releaseDriver(assignedDriver);
        log.info("Ride {} cancelled by passenger", ride.getId());
        return recorder.record(ride, change, ActorType.PASSENGER, passengerId, reason);
    }

    private Ride cancelByDriver(Ride ride, UUID driverId, String reason, Instant now) {
        if (REDISPATCHABLE.contains(ride.getStatus())) {
            Ride.StatusChange change = ride.redispatch();
            offers.findByRideIdAndDriverId(ride.getId(), driverId).ifPresent(offer -> offer.withdraw(now));
            releaseDriver(driverId);
            Ride saved = recorder.record(ride, change, ActorType.DRIVER, driverId, reason);
            events.publish(new MatchingRoundRequestedEvent(ride.getId()));
            log.info("Driver {} withdrew from ride {}; re-dispatching", driverId, ride.getId());
            return saved;
        }
        if (ride.getStatus() == RideStatus.DRIVER_ARRIVED
                && now.isBefore(ride.getArrivedAt().plus(properties.noShowWait()))) {
            throw new InvalidStateException(ErrorCode.NO_SHOW_WAIT_NOT_ELAPSED,
                    "Wait at least " + properties.noShowWait().toMinutes() + " minutes after arriving before cancelling");
        }
        String effectiveReason = reason == null || reason.isBlank() ? NO_SHOW_REASON : reason;
        Ride.StatusChange change = ride.cancel(ActorType.DRIVER, effectiveReason, now);
        releaseDriver(driverId);
        return recorder.record(ride, change, ActorType.DRIVER, driverId, effectiveReason);
    }

    private void releaseDriver(UUID driverId) {
        if (driverId != null) {
            drivers.findById(driverId).ifPresent(Driver::endTrip);
        }
    }
}
