package com.rideflow.repository;

import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Composable, parameter-bound filters for the admin ride search. A {@code null} argument means "any". */
public final class RideSpecifications {

    private static final String REQUESTED_AT = "requestedAt";

    private RideSpecifications() {
    }

    public static Specification<Ride> hasStatus(RideStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Ride> requestedFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get(REQUESTED_AT), from);
    }

    public static Specification<Ride> requestedBefore(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThan(root.get(REQUESTED_AT), to);
    }

    public static Specification<Ride> hasPassenger(UUID passengerId) {
        return (root, query, cb) -> passengerId == null ? null : cb.equal(root.get("passengerId"), passengerId);
    }

    public static Specification<Ride> hasDriver(UUID driverId) {
        return (root, query, cb) -> driverId == null ? null : cb.equal(root.get("driverId"), driverId);
    }
}
