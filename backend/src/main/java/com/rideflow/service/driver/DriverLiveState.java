package com.rideflow.service.driver;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.rideflow.entity.DriverAvailability;
import com.rideflow.entity.RideStatus;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.service.ride.EtaDestination;
import java.util.Optional;
import java.util.UUID;

/**
 * What location ingestion needs to know about a driver on every report, cached in Redis
 * ({@code driver:{id}:state}) so that reports do not read PostgreSQL.
 *
 * @param activeRide the ride the driver is engaged in, or {@code null}
 */
public record DriverLiveState(DriverAvailability availability, ActiveRide activeRide) {

    @JsonIgnore
    public boolean isOnline() {
        return availability != DriverAvailability.OFFLINE;
    }

    /** The ride fields a location report is routed and timed with. */
    public record ActiveRide(UUID rideId, UUID passengerId, RideStatus status, GeoPoint pickup, GeoPoint dropoff) {

        public Optional<EtaDestination> destination() {
            return EtaDestination.of(status, pickup, dropoff);
        }
    }
}
