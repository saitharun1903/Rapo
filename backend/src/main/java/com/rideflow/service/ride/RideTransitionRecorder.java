package com.rideflow.service.ride;

import com.rideflow.entity.ActorType;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatusEvent;
import com.rideflow.repository.RideRepository;
import com.rideflow.repository.RideStatusEventRepository;
import com.rideflow.service.driver.DriverStateCache;
import com.rideflow.service.event.DomainEventPublisher;
import com.rideflow.service.ride.event.RideStatusChangedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a status change together with its history row and domain event, in the caller's transaction.
 * Flushing first surfaces optimistic-lock conflicts immediately and yields the ride's new version. Once the
 * change commits, the ride's cached ETA is dropped and the cached state of the drivers involved is rewritten.
 */
@Component
public class RideTransitionRecorder {

    private final RideRepository rides;
    private final RideStatusEventRepository statusEvents;
    private final DomainEventPublisher events;
    private final LiveEtaService liveEta;
    private final DriverStateCache driverStates;
    private final Clock clock;

    public RideTransitionRecorder(RideRepository rides, RideStatusEventRepository statusEvents,
                                  DomainEventPublisher events, LiveEtaService liveEta,
                                  DriverStateCache driverStates, Clock clock) {
        this.rides = rides;
        this.statusEvents = statusEvents;
        this.events = events;
        this.liveEta = liveEta;
        this.driverStates = driverStates;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Ride record(Ride ride, Ride.StatusChange change, ActorType actor, UUID actorUserId, String reason) {
        Ride saved = rides.saveAndFlush(ride);
        Instant now = clock.instant();
        statusEvents.save(new RideStatusEvent(saved.getId(), change.from(), change.to(), actor, actorUserId, reason,
                saved.getVersion(), now));
        events.publish(new RideStatusChangedEvent(saved.getId(), change.from(), change.to(), saved.getVersion(),
                saved.getPassengerId(), saved.getDriverId(), change.releasedDriverId(), actor, reason, now));
        liveEta.evictAfterCommit(saved.getId());
        driverStates.refreshAfterCommit(saved.getDriverId(), change.releasedDriverId());
        return saved;
    }
}
