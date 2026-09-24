package com.rideflow.service.matching;

import com.rideflow.config.MatchingProperties;
import com.rideflow.entity.ActorType;
import com.rideflow.entity.OfferStatus;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import com.rideflow.repository.DriverLocationRepository;
import com.rideflow.repository.NearbyDriver;
import com.rideflow.repository.RideOfferRepository;
import com.rideflow.repository.RideRepository;
import com.rideflow.service.event.DomainEventPublisher;
import com.rideflow.service.ride.RideTransitionRecorder;
import com.rideflow.service.ride.event.RideOffersCreatedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs matching rounds: PostGIS finds the nearest eligible drivers, and the ride is offered to the first
 * few of them. Round n widens the radius; after the last round without an acceptance the ride expires.
 * Flow: docs/architecture.md section 7.3.
 */
@Service
public class DriverMatchingService {

    private static final Logger log = LoggerFactory.getLogger(DriverMatchingService.class);

    private final RideRepository rides;
    private final RideOfferRepository offers;
    private final DriverLocationRepository driverLocations;
    private final RideTransitionRecorder recorder;
    private final DomainEventPublisher events;
    private final MatchingProperties properties;
    private final Clock clock;

    public DriverMatchingService(RideRepository rides, RideOfferRepository offers,
                                 DriverLocationRepository driverLocations, RideTransitionRecorder recorder,
                                 DomainEventPublisher events, MatchingProperties properties, Clock clock) {
        this.rides = rides;
        this.offers = offers;
        this.driverLocations = driverLocations;
        this.recorder = recorder;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Starts the ride's next matching round if it still needs one. Safe to call repeatedly and
     * concurrently (after-commit trigger, sweeper, other instances): the ride row is locked and every
     * precondition is re-checked under the lock.
     */
    @Transactional
    public void runNextRound(UUID rideId) {
        Instant now = clock.instant();
        Ride ride = rides.findByIdForUpdate(rideId).orElse(null);
        if (ride == null || !RideStatus.AWAITING_DRIVER.contains(ride.getStatus())) {
            return;
        }
        if (offers.existsByRideIdAndStatusAndExpiresAtAfter(rideId, OfferStatus.PENDING, now)) {
            return;
        }
        offers.expireOverdueForRide(rideId, now);

        int round = ride.getMatchingRound() + 1;
        if (round > properties.maxRounds()) {
            recorder.record(ride, ride.expire(now), ActorType.SYSTEM, null,
                    "No driver accepted after " + properties.maxRounds() + " matching rounds");
            log.info("Ride {} expired: no driver accepted after {} rounds", rideId, properties.maxRounds());
            return;
        }

        int radius = radiusForRound(round);
        Ride.StatusChange change = ride.beginMatchingRound(round, radius, now);
        if (change != null) {
            recorder.record(ride, change, ActorType.SYSTEM, null, null);
        }

        List<NearbyDriver> candidates = driverLocations.findAvailableNear(ride.getPickup(), radius,
                ride.getVehicleCategory(), now.minus(properties.locationFreshness()), rideId, properties.candidateLimit());
        Instant expiresAt = now.plus(properties.offerTtl());
        List<UUID> offeredTo = new ArrayList<>();
        for (NearbyDriver candidate : candidates) {
            if (offeredTo.size() == properties.offersPerRound()) {
                break;
            }
            int inserted = offers.insertIfAbsent(rideId, candidate.driverId(), round,
                    (int) Math.round(candidate.distanceMeters()), now, expiresAt);
            if (inserted == 1) {
                offeredTo.add(candidate.driverId());
            }
        }

        log.info("Ride {} round {} (radius {} m): {} candidates, offered to {}",
                rideId, round, radius, candidates.size(), offeredTo.size());
        if (!offeredTo.isEmpty()) {
            events.publish(new RideOffersCreatedEvent(rideId, round, offeredTo, expiresAt));
        }
    }

    int radiusForRound(int round) {
        double radius = properties.initialRadiusMeters() * Math.pow(properties.radiusGrowth(), round - 1);
        return (int) Math.min(properties.maxRadiusMeters(), Math.round(radius));
    }
}
