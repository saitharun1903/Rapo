package com.rideflow.service.ride;

import com.rideflow.config.MatchingProperties;
import com.rideflow.config.RideProperties;
import com.rideflow.dto.ride.RideResponse;
import com.rideflow.entity.ActorType;
import com.rideflow.entity.DistanceSource;
import com.rideflow.entity.Driver;
import com.rideflow.entity.FareBreakdown;
import com.rideflow.entity.FareKind;
import com.rideflow.entity.OfferStatus;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideOffer;
import com.rideflow.entity.RideStatus;
import com.rideflow.entity.Vehicle;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import com.rideflow.exception.ResourceNotFoundException;
import com.rideflow.exception.RideFlowException;
import com.rideflow.geospatial.GeoMath;
import com.rideflow.monitoring.RideMetrics;
import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.FareBreakdownRepository;
import com.rideflow.repository.RideOfferRepository;
import com.rideflow.repository.RideTrackPointRepository.TrackSummary;
import com.rideflow.repository.RideTrackPointRepository;
import com.rideflow.repository.VehicleRepository;
import com.rideflow.service.driver.DriverPositions;
import com.rideflow.service.event.DomainEventPublisher;
import com.rideflow.service.fare.FareCalculator;
import com.rideflow.service.ride.event.MatchingRoundRequestedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Everything a driver does to a ride: respond to offers, then drive it through to completion. */
@Service
@Transactional
public class DriverRideService {

    private static final Logger log = LoggerFactory.getLogger(DriverRideService.class);
    /** A trip line needs at least two points to have a length. */
    private static final int MIN_TRACK_POINTS = 2;

    private final RideAccessPolicy access;
    private final RideOfferRepository offers;
    private final RideOfferWithdrawal offerWithdrawal;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final DriverPositions positions;
    private final RideTrackPointRepository trackPoints;
    private final FareBreakdownRepository fareBreakdowns;
    private final FareCalculator fareCalculator;
    private final RideTransitionRecorder recorder;
    private final DomainEventPublisher events;
    private final RideViewAssembler views;
    private final RideMetrics metrics;
    private final RideProperties rideProperties;
    private final MatchingProperties matchingProperties;
    private final Clock clock;

    public DriverRideService(RideAccessPolicy access, RideOfferRepository offers, RideOfferWithdrawal offerWithdrawal,
                             DriverRepository drivers,
                             VehicleRepository vehicles, DriverPositions positions,
                             RideTrackPointRepository trackPoints, FareBreakdownRepository fareBreakdowns,
                             FareCalculator fareCalculator, RideTransitionRecorder recorder,
                             DomainEventPublisher events, RideViewAssembler views, RideMetrics metrics,
                             RideProperties rideProperties,
                             MatchingProperties matchingProperties, Clock clock) {
        this.access = access;
        this.offers = offers;
        this.offerWithdrawal = offerWithdrawal;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.positions = positions;
        this.trackPoints = trackPoints;
        this.fareBreakdowns = fareBreakdowns;
        this.fareCalculator = fareCalculator;
        this.recorder = recorder;
        this.events = events;
        this.views = views;
        this.metrics = metrics;
        this.rideProperties = rideProperties;
        this.matchingProperties = matchingProperties;
        this.clock = clock;
    }

    /**
     * Accepts an open offer. Concurrent accepts for the same ride serialise on the ride row lock: the first
     * wins and the others then see an assigned ride and get RIDE_ALREADY_ASSIGNED. The optimistic version
     * and the one-accepted-offer index remain as backstops.
     */
    public RideResponse accept(UUID driverId, UUID rideId) {
        Instant now = clock.instant();
        Ride ride = access.lockOfferedTo(driverId, rideId);
        RideOffer offer = offers.findByRideIdAndDriverId(rideId, driverId).orElseThrow(RideAccessPolicy::notFound);
        if (offer.getStatus() == OfferStatus.ACCEPTED && ride.isAssignedTo(driverId)) {
            return views.toResponse(ride);
        }
        if (RideStatus.DRIVER_ENGAGED.contains(ride.getStatus())) {
            throw alreadyAssigned();
        }
        if (!RideStatus.AWAITING_DRIVER.contains(ride.getStatus())) {
            throw new InvalidStateException(ErrorCode.OFFER_EXPIRED, "This ride is no longer available");
        }
        offer.accept(now);
        metrics.offersClosed(OfferStatus.ACCEPTED, 1);
        Driver driver = loadDriver(driverId);
        driver.startTrip();
        Vehicle vehicle = vehicles.findByDriverIdAndActiveTrue(driverId).orElseThrow(() ->
                new InvalidStateException(ErrorCode.NO_ACTIVE_VEHICLE, "Register an active vehicle first"));
        Ride.StatusChange change = ride.assignDriver(driverId, vehicle.getId(), now);
        try {
            ride = recorder.record(ride, change, ActorType.DRIVER, driverId, null);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException ex) {
            throw alreadyAssigned();
        }
        offerWithdrawal.withdrawPending(rideId, now);
        log.info("Ride {} accepted by driver {}", rideId, driverId);
        return views.toResponse(ride);
    }

    public void reject(UUID driverId, UUID rideId) {
        Instant now = clock.instant();
        RideOffer offer = offers.findByRideIdAndDriverId(rideId, driverId).orElseThrow(RideAccessPolicy::notFound);
        offer.reject(now);
        metrics.offersClosed(OfferStatus.REJECTED, 1);
        if (!offers.existsByRideIdAndStatusAndExpiresAtAfter(rideId, OfferStatus.PENDING, now)) {
            events.publish(new MatchingRoundRequestedEvent(rideId));
        }
    }

    public RideResponse markEnRoute(UUID driverId, UUID rideId) {
        Ride ride = access.lockAssignedTo(driverId, rideId);
        return views.toResponse(recorder.record(ride, ride.markEnRoute(clock.instant()), ActorType.DRIVER, driverId, null));
    }

    /** Only allowed when the driver's latest fresh position is within the pickup geofence. */
    public RideResponse markArrived(UUID driverId, UUID rideId) {
        Instant now = clock.instant();
        Ride ride = access.lockAssignedTo(driverId, rideId);
        double distance = positions.latest(driverId)
                .filter(position -> position.updatedAt().isAfter(freshSince(now)))
                .map(position -> GeoMath.haversineMeters(position.point(), ride.getPickup()))
                .orElseThrow(() -> new RideFlowException(ErrorCode.LOCATION_UNAVAILABLE,
                        "No recent location from your device; check GPS and try again"));
        if (distance > rideProperties.pickupGeofenceMeters()) {
            throw new RideFlowException(ErrorCode.NOT_AT_PICKUP, String.format(Locale.ROOT,
                    "You are %.0f m from the pickup point; arrive within %d m first",
                    distance, rideProperties.pickupGeofenceMeters()));
        }
        return views.toResponse(recorder.record(ride, ride.markArrived(now), ActorType.DRIVER, driverId, null));
    }

    public RideResponse start(UUID driverId, UUID rideId) {
        Instant now = clock.instant();
        Ride ride = access.lockAssignedTo(driverId, rideId);
        ride = recorder.record(ride, ride.start(now), ActorType.DRIVER, driverId, null);
        recordCurrentPosition(driverId, ride.getId(), now);
        return views.toResponse(ride);
    }

    /**
     * Completes the trip. Distance is the PostGIS length of the recorded GPS trail when there is one;
     * otherwise the routed estimate is used and flagged as such. The final fare reuses the surge locked
     * at booking.
     */
    public RideResponse complete(UUID driverId, UUID rideId) {
        Instant now = clock.instant();
        Ride ride = access.lockAssignedTo(driverId, rideId);
        recordCurrentPosition(driverId, rideId, now);
        TrackSummary track = trackPoints.summarize(rideId);
        boolean tracked = track.points() >= MIN_TRACK_POINTS && track.lengthMeters() > 0;
        int distanceMeters = tracked ? (int) Math.round(track.lengthMeters()) : ride.getEstimatedDistanceMeters();

        Ride.StatusChange change = ride.complete(distanceMeters,
                tracked ? DistanceSource.TRACKED : DistanceSource.ESTIMATED, now);
        FareBreakdown.Amounts fare = fareCalculator.calculate(ride.getVehicleCategory(), distanceMeters,
                ride.getActualDurationSeconds(), ride.getSurgeMultiplier());
        loadDriver(driverId).endTrip();
        ride = recorder.record(ride, change, ActorType.DRIVER, driverId, null);
        fareBreakdowns.save(FareBreakdown.of(rideId, FareKind.FINAL, fare));
        log.info("Ride {} completed: {} m ({}), {} s, fare {} {}", rideId, distanceMeters, ride.getDistanceSource(),
                ride.getActualDurationSeconds(), fare.total(), fare.currency());
        return views.toResponse(ride);
    }

    private void recordCurrentPosition(UUID driverId, UUID rideId, Instant now) {
        positions.latest(driverId)
                .filter(position -> position.updatedAt().isAfter(freshSince(now)))
                .ifPresent(position -> trackPoints.append(rideId, position.point(), now));
    }

    private Instant freshSince(Instant now) {
        return now.minus(matchingProperties.locationFreshness());
    }

    private Driver loadDriver(UUID driverId) {
        return drivers.findById(driverId).orElseThrow(() ->
                new ResourceNotFoundException(ErrorCode.DRIVER_PROFILE_NOT_FOUND, "Driver profile not found"));
    }

    private static InvalidStateException alreadyAssigned() {
        return new InvalidStateException(ErrorCode.RIDE_ALREADY_ASSIGNED, "Another driver has already accepted this ride");
    }
}
