package com.rideflow.service.ride;

import com.rideflow.dto.ride.EtaResponse;
import com.rideflow.entity.Ride;
import com.rideflow.geospatial.GeoPoint;
import java.util.Optional;

/** Where the driver is heading next: the pickup until the trip starts, then the dropoff. */
public record EtaDestination(EtaResponse.Target target, GeoPoint point) {

    /** Empty when there is no next stop to time (waiting at the pickup, or no driver engaged). */
    public static Optional<EtaDestination> of(Ride ride) {
        return switch (ride.getStatus()) {
            case DRIVER_ASSIGNED, DRIVER_ARRIVING ->
                    Optional.of(new EtaDestination(EtaResponse.Target.PICKUP, ride.getPickup()));
            case IN_PROGRESS -> Optional.of(new EtaDestination(EtaResponse.Target.DROPOFF, ride.getDropoff()));
            default -> Optional.empty();
        };
    }
}
