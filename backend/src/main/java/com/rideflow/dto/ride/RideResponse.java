package com.rideflow.dto.ride;

import com.rideflow.dto.common.Money;
import com.rideflow.dto.fare.FareBreakdownResponse;
import com.rideflow.entity.ActorType;
import com.rideflow.entity.DistanceSource;
import com.rideflow.entity.EstimateSource;
import com.rideflow.entity.PaymentMethod;
import com.rideflow.entity.PaymentProvider;
import com.rideflow.entity.PaymentStatus;
import com.rideflow.entity.RideStatus;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.geospatial.GeoPoint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Full view of a ride. {@code version} increases with every status change; clients apply realtime
 * updates only if they carry a newer version.
 */
public record RideResponse(
        UUID id,
        RideStatus status,
        long version,
        VehicleCategory vehicleCategory,
        Place pickup,
        Place dropoff,
        PaymentMethod paymentMethod,
        Estimate estimate,
        Actual actual,
        PaymentInfo payment,
        DriverInfo driver,
        PassengerInfo passenger,
        Matching matching,
        Timestamps timestamps,
        Cancellation cancellation) {

    public record Place(GeoPoint point, String address) {
    }

    public record Estimate(int distanceMeters, int durationSeconds, EstimateSource source, Money fare,
                           FareBreakdownResponse breakdown) {
    }

    /** Present once the ride is completed. */
    public record Actual(int distanceMeters, int durationSeconds, DistanceSource distanceSource, Money fare,
                         FareBreakdownResponse breakdown) {
    }

    /**
     * Present once the completed ride has been settled (asynchronously, shortly after completion).
     * {@code provider} {@code SANDBOX} marks a simulated card payment: no money moved.
     */
    public record PaymentInfo(UUID id, PaymentMethod method, PaymentStatus status, PaymentProvider provider,
                              Money amount) {
    }

    public record DriverInfo(UUID id, String fullName, BigDecimal ratingAvg, int ratingCount, Vehicle vehicle) {
    }

    public record Vehicle(String make, String model, String color, String plateNumber, VehicleCategory category) {
    }

    public record PassengerInfo(UUID id, String fullName) {
    }

    public record Matching(int round, int radiusMeters) {
    }

    public record Timestamps(Instant requestedAt, Instant acceptedAt, Instant enRouteAt, Instant arrivedAt,
                             Instant startedAt, Instant completedAt, Instant cancelledAt, Instant expiredAt) {
    }

    public record Cancellation(ActorType cancelledBy, String reason) {
    }
}
