package com.rideflow.entity;

import static com.rideflow.entity.ActorType.ADMIN;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Checks the complete transition table: every (from, to, actor) triple is either listed here or forbidden. */
class RideStateMachineTest {

    private record Edge(RideStatus from, RideStatus to, ActorType actor) {
    }

    private static final Set<Edge> ALLOWED = Set.of(
            new Edge(REQUESTED, MATCHING, SYSTEM),
            new Edge(REQUESTED, CANCELLED, PASSENGER),
            new Edge(REQUESTED, EXPIRED, SYSTEM),
            new Edge(MATCHING, DRIVER_ASSIGNED, DRIVER),
            new Edge(MATCHING, CANCELLED, PASSENGER),
            new Edge(MATCHING, EXPIRED, SYSTEM),
            new Edge(DRIVER_ASSIGNED, DRIVER_ARRIVING, DRIVER),
            new Edge(DRIVER_ASSIGNED, MATCHING, DRIVER),
            new Edge(DRIVER_ASSIGNED, CANCELLED, PASSENGER),
            new Edge(DRIVER_ARRIVING, DRIVER_ARRIVED, DRIVER),
            new Edge(DRIVER_ARRIVING, MATCHING, DRIVER),
            new Edge(DRIVER_ARRIVING, CANCELLED, PASSENGER),
            new Edge(DRIVER_ARRIVED, IN_PROGRESS, DRIVER),
            new Edge(DRIVER_ARRIVED, CANCELLED, PASSENGER),
            new Edge(DRIVER_ARRIVED, CANCELLED, DRIVER),
            new Edge(IN_PROGRESS, COMPLETED, DRIVER));

    @Test
    void transitionTableMatchesSpecificationExactly() {
        Set<Edge> actual = new HashSet<>();
        for (RideStatus from : RideStatus.values()) {
            for (RideStatus to : RideStatus.values()) {
                for (ActorType actor : ActorType.values()) {
                    if (RideStateMachine.isAllowed(from, to, actor)) {
                        actual.add(new Edge(from, to, actor));
                    }
                }
            }
        }
        assertThat(actual).isEqualTo(ALLOWED);
    }

    @Test
    void terminalStatusesHaveNoOutgoingTransitions() {
        for (RideStatus terminal : EnumSet.of(COMPLETED, CANCELLED, EXPIRED)) {
            assertThat(terminal.isTerminal()).isTrue();
            for (RideStatus to : RideStatus.values()) {
                for (ActorType actor : ActorType.values()) {
                    assertThat(RideStateMachine.isAllowed(terminal, to, actor)).isFalse();
                }
            }
        }
    }

    @Test
    void completedRideCanNeverBeRequestedAgain() {
        assertThatThrownBy(() -> RideStateMachine.requireAllowed(COMPLETED, REQUESTED, ADMIN))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("COMPLETED to REQUESTED")
                .extracting(ex -> ((InvalidStateException) ex).code())
                .isEqualTo(ErrorCode.RIDE_INVALID_TRANSITION);
    }

    @Test
    void passengerCannotDriveTheRideForward() {
        assertThat(RideStateMachine.isAllowed(IN_PROGRESS, COMPLETED, PASSENGER)).isFalse();
        assertThat(RideStateMachine.isAllowed(IN_PROGRESS, CANCELLED, PASSENGER)).isFalse();
    }

    @Test
    void activeAndEngagedSetsAreConsistentWithTheTable() {
        assertThat(RideStatus.ACTIVE).doesNotContain(COMPLETED, CANCELLED, EXPIRED);
        assertThat(RideStatus.ACTIVE).containsAll(RideStatus.DRIVER_ENGAGED);
        assertThat(RideStatus.ACTIVE).containsAll(RideStatus.AWAITING_DRIVER);
    }
}
