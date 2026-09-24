package com.rideflow.service.ride;

import com.rideflow.config.MatchingProperties;
import com.rideflow.dto.ride.EtaResponse;
import com.rideflow.dto.ride.RideTrackingResponse;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import com.rideflow.entity.Role;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import com.rideflow.repository.DriverLocationRepository;
import com.rideflow.repository.DriverPosition;
import com.rideflow.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The tracking snapshot. Deliberately not one transaction: the reads are independent, and a routed ETA
 * (on a cache miss) is an outbound HTTP call that must not hold a database connection while it waits.
 */
@Service
public class RideTrackingService {

    private final RideAccessPolicy access;
    private final DriverLocationRepository driverLocations;
    private final LiveEtaService liveEta;
    private final MatchingProperties matchingProperties;
    private final Clock clock;

    public RideTrackingService(RideAccessPolicy access, DriverLocationRepository driverLocations,
                               LiveEtaService liveEta, MatchingProperties matchingProperties, Clock clock) {
        this.access = access;
        this.driverLocations = driverLocations;
        this.liveEta = liveEta;
        this.matchingProperties = matchingProperties;
        this.clock = clock;
    }

    /** Visible to the passenger, the assigned driver and admins; a driver who only got an offer sees 404. */
    public RideTrackingResponse snapshot(AuthenticatedUser user, UUID rideId) {
        Ride ride = access.loadVisible(user, rideId);
        if (user.role() == Role.DRIVER && !ride.isAssignedTo(user.id())) {
            throw RideAccessPolicy.notFound();
        }
        if (!RideStatus.DRIVER_ENGAGED.contains(ride.getStatus())) {
            throw new InvalidStateException(ErrorCode.TRACKING_UNAVAILABLE,
                    "Live tracking is available while a driver is assigned to the ride");
        }
        DriverPosition position = driverLocations.find(ride.getDriverId()).orElse(null);
        if (position == null) {
            return new RideTrackingResponse(rideId, ride.getStatus(), null, true, null);
        }
        Instant freshSince = clock.instant().minus(matchingProperties.locationFreshness());
        boolean stale = !position.updatedAt().isAfter(freshSince);
        EtaResponse eta = stale ? null : EtaDestination.of(ride)
                .map(destination -> liveEta.current(rideId, position.point(), destination))
                .orElse(null);
        return new RideTrackingResponse(rideId, ride.getStatus(),
                new RideTrackingResponse.DriverLocation(position.point(), position.headingDeg(), position.recordedAt()),
                stale, eta);
    }
}
