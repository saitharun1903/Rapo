package com.rideflow.entity;

import static com.rideflow.entity.ActorType.DRIVER;
import static com.rideflow.entity.ActorType.PASSENGER;
import static com.rideflow.entity.ActorType.SYSTEM;
import static com.rideflow.entity.RideStatus.CANCELLED;
import static com.rideflow.entity.RideStatus.COMPLETED;
import static com.rideflow.entity.RideStatus.DRIVER_ARRIVED;
import static com.rideflow.entity.RideStatus.DRIVER_ARRIVING;
import static com.rideflow.entity.RideStatus.DRIVER_ASSIGNED;
import static com.rideflow.entity.RideStatus.EXPIRED;
import static com.rideflow.entity.RideStatus.IN_PROGRESS;
import static com.rideflow.entity.RideStatus.MATCHING;
import static com.rideflow.entity.RideStatus.REQUESTED;

import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for legal ride status transitions and which actor may perform each one.
 * Diagram: docs/architecture.md section 6. {@link Ride} is the only caller; nothing else can change a
 * ride's status.
 */
public final class RideStateMachine {

    private static final Map<RideStatus, Map<RideStatus, Set<ActorType>>> TRANSITIONS = buildTransitions();

    private RideStateMachine() {
    }

    private static Map<RideStatus, Map<RideStatus, Set<ActorType>>> buildTransitions() {
        Map<RideStatus, Map<RideStatus, Set<ActorType>>> table = new EnumMap<>(RideStatus.class);
        allow(table, REQUESTED, MATCHING, SYSTEM);
        allow(table, REQUESTED, CANCELLED, PASSENGER);
        allow(table, REQUESTED, EXPIRED, SYSTEM);

        allow(table, MATCHING, DRIVER_ASSIGNED, DRIVER);
        allow(table, MATCHING, CANCELLED, PASSENGER);
        allow(table, MATCHING, EXPIRED, SYSTEM);

        allow(table, DRIVER_ASSIGNED, DRIVER_ARRIVING, DRIVER);
        allow(table, DRIVER_ASSIGNED, MATCHING, DRIVER);        // driver backs out: re-dispatch
        allow(table, DRIVER_ASSIGNED, CANCELLED, PASSENGER);

        allow(table, DRIVER_ARRIVING, DRIVER_ARRIVED, DRIVER);
        allow(table, DRIVER_ARRIVING, MATCHING, DRIVER);        // driver backs out: re-dispatch
        allow(table, DRIVER_ARRIVING, CANCELLED, PASSENGER);

        allow(table, DRIVER_ARRIVED, IN_PROGRESS, DRIVER);
        allow(table, DRIVER_ARRIVED, CANCELLED, PASSENGER, DRIVER); // driver only after the no-show wait

        allow(table, IN_PROGRESS, COMPLETED, DRIVER);

        table.replaceAll((from, targets) -> Collections.unmodifiableMap(targets));
        return Collections.unmodifiableMap(table);
    }

    private static void allow(
            Map<RideStatus, Map<RideStatus, Set<ActorType>>> table, RideStatus from, RideStatus to, ActorType... actors) {
        Set<ActorType> allowed = EnumSet.noneOf(ActorType.class);
        allowed.addAll(Set.of(actors));
        table.computeIfAbsent(from, status -> new EnumMap<>(RideStatus.class)).put(to, Collections.unmodifiableSet(allowed));
    }

    public static boolean isAllowed(RideStatus from, RideStatus to, ActorType actor) {
        return TRANSITIONS.getOrDefault(from, Map.of()).getOrDefault(to, Set.of()).contains(actor);
    }

    public static void requireAllowed(RideStatus from, RideStatus to, ActorType actor) {
        if (!isAllowed(from, to, actor)) {
            throw new InvalidStateException(ErrorCode.RIDE_INVALID_TRANSITION,
                    "Ride cannot move from " + from + " to " + to + " by " + actor);
        }
    }
}
