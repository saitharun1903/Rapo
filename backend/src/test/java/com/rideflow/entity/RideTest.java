package com.rideflow.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.exception.InvalidStateException;
import com.rideflow.geospatial.GeoPoint;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RideTest {

    private static final Instant T0 = Instant.parse("2026-09-24T10:00:00Z");
    private static final UUID DRIVER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();

    private static Ride newRide() {
        return Ride.request(new Ride.RequestDetails(UUID.randomUUID(), VehicleCategory.ECONOMY,
                new GeoPoint(17.44, 78.38), "Pickup", new GeoPoint(17.42, 78.47), "Dropoff", PaymentMethod.CASH,
                12_000, 1_500, EstimateSource.ROUTED, new BigDecimal("1.20"), "INR"), T0);
    }

    @Test
    void happyPathRecordsEveryTimestampAndMeasuredTrip() {
        Ride ride = newRide();
        assertThat(ride.getStatus()).isEqualTo(RideStatus.REQUESTED);

        assertThat(ride.beginMatchingRound(1, 3000, T0)).isEqualTo(new Ride.StatusChange(RideStatus.REQUESTED, RideStatus.MATCHING));
        ride.assignDriver(DRIVER_ID, VEHICLE_ID, T0.plusSeconds(10));
        ride.markEnRoute(T0.plusSeconds(15));
        ride.markArrived(T0.plusSeconds(300));
        ride.start(T0.plusSeconds(360));
        Ride.StatusChange change = ride.complete(11_800, DistanceSource.TRACKED, T0.plusSeconds(360 + 1_620));

        assertThat(change).isEqualTo(new Ride.StatusChange(RideStatus.IN_PROGRESS, RideStatus.COMPLETED));
        assertThat(ride.isAssignedTo(DRIVER_ID)).isTrue();
        assertThat(ride.getActualDurationSeconds()).isEqualTo(1_620);
        assertThat(ride.getActualDistanceMeters()).isEqualTo(11_800);
        assertThat(ride.getAcceptedAt()).isEqualTo(T0.plusSeconds(10));
        assertThat(ride.getArrivedAt()).isEqualTo(T0.plusSeconds(300));
    }

    @Test
    void laterMatchingRoundsDoNotChangeStatusButWidenRadius() {
        Ride ride = newRide();
        ride.beginMatchingRound(1, 3000, T0);

        assertThat(ride.beginMatchingRound(2, 4500, T0.plusSeconds(20))).isNull();
        assertThat(ride.getMatchingRound()).isEqualTo(2);
        assertThat(ride.getMatchingRadiusMeters()).isEqualTo(4500);
        assertThat(ride.getRoundStartedAt()).isEqualTo(T0.plusSeconds(20));
    }

    @Test
    void redispatchDetachesDriverAndRestartsMatching() {
        Ride ride = newRide();
        ride.beginMatchingRound(1, 3000, T0);
        ride.assignDriver(DRIVER_ID, VEHICLE_ID, T0);

        ride.redispatch();

        assertThat(ride.getStatus()).isEqualTo(RideStatus.MATCHING);
        assertThat(ride.getDriverId()).isNull();
        assertThat(ride.getMatchingRound()).isZero();
        assertThat(ride.getRoundStartedAt()).isNull();
    }

    @Test
    void cannotStartWithoutArriving() {
        Ride ride = newRide();
        ride.beginMatchingRound(1, 3000, T0);
        ride.assignDriver(DRIVER_ID, VEHICLE_ID, T0);

        assertThatThrownBy(() -> ride.start(T0)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void cancelRecordsWhoAndWhy() {
        Ride ride = newRide();

        ride.cancel(ActorType.PASSENGER, "Plans changed", T0.plus(Duration.ofMinutes(1)));

        assertThat(ride.getStatus()).isEqualTo(RideStatus.CANCELLED);
        assertThat(ride.getCancelledBy()).isEqualTo(ActorType.PASSENGER);
        assertThat(ride.getCancellationReason()).isEqualTo("Plans changed");
    }

    @Test
    void completedRideCannotBeCancelled() {
        Ride ride = newRide();
        ride.beginMatchingRound(1, 3000, T0);
        ride.assignDriver(DRIVER_ID, VEHICLE_ID, T0);
        ride.markEnRoute(T0);
        ride.markArrived(T0);
        ride.start(T0);
        ride.complete(100, DistanceSource.ESTIMATED, T0.plusSeconds(60));

        assertThatThrownBy(() -> ride.cancel(ActorType.PASSENGER, null, T0)).isInstanceOf(InvalidStateException.class);
    }
}
