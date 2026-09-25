package com.rideflow.service.ride;

import com.rideflow.config.MatchingProperties;
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
    /** How far outside the area a driver can be and still be matched: the widest matching radius. */
    private final int driverReachMeters;

    public ServiceAreaPolicy(RideProperties properties, MatchingProperties matching) {
        this.serviceArea = properties.serviceArea();
        this.minTripDistanceMeters = properties.minTripDistanceMeters();
        this.driverReachMeters = matching.maxRadiusMeters();
    }

    public void validateTrip(GeoPoint pickup, GeoPoint dropoff) {
        requireInside(pickup, "Pickup");
        requireInside(dropoff, "Destination");
        if (GeoMath.haversineMeters(pickup, dropoff) < minTripDistanceMeters) {
            throw new RideFlowException(ErrorCode.PICKUP_EQUALS_DROPOFF,
                    "Pickup and destination must be at least " + minTripDistanceMeters + " m apart");
        }
    }

    /**
     * A route preview ends at a ride's stop, inside the area, and starts at most a matching radius outside it
     * (a driver heading for a pickup). Anything else is not a route the app needs from the router.
     */
    public void validateRoute(GeoPoint from, GeoPoint to) {
        requireWithin(from, serviceArea.radiusMeters() + driverReachMeters, "Route start");
        requireInside(to, "Route end");
    }

    private void requireInside(GeoPoint point, String label) {
        requireWithin(point, serviceArea.radiusMeters(), label);
    }

    private void requireWithin(GeoPoint point, int radiusMeters, String label) {
        if (GeoMath.haversineMeters(serviceArea.center(), point) > radiusMeters) {
            throw new RideFlowException(ErrorCode.OUTSIDE_SERVICE_AREA, label + " is outside the service area");
        }
    }
}
