package com.rideflow.service.ride;

import com.rideflow.config.RideProperties;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import com.rideflow.geospatial.GeoMath;
import com.rideflow.geospatial.GeoPoint;
import org.springframework.stereotype.Component;

/** Where rides may start and end, and the minimum sensible trip. */
@Component
public class ServiceAreaPolicy {

    private final RideProperties.ServiceArea serviceArea;
    private final int minTripDistanceMeters;

    public ServiceAreaPolicy(RideProperties properties) {
        this.serviceArea = properties.serviceArea();
        this.minTripDistanceMeters = properties.minTripDistanceMeters();
    }

    public void validateTrip(GeoPoint pickup, GeoPoint dropoff) {
        requireInside(pickup, "Pickup");
        requireInside(dropoff, "Destination");
        if (GeoMath.haversineMeters(pickup, dropoff) < minTripDistanceMeters) {
            throw new RideFlowException(ErrorCode.PICKUP_EQUALS_DROPOFF,
                    "Pickup and destination must be at least " + minTripDistanceMeters + " m apart");
        }
    }

    private void requireInside(GeoPoint point, String label) {
        if (GeoMath.haversineMeters(serviceArea.center(), point) > serviceArea.radiusMeters()) {
            throw new RideFlowException(ErrorCode.OUTSIDE_SERVICE_AREA, label + " is outside the service area");
        }
    }
}
