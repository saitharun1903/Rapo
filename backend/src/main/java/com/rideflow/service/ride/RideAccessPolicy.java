package com.rideflow.service.ride;

import com.rideflow.entity.OfferStatus;
import com.rideflow.entity.Ride;
import com.rideflow.entity.Role;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.ResourceNotFoundException;
import com.rideflow.repository.RideOfferRepository;
import com.rideflow.repository.RideRepository;
import com.rideflow.security.AuthenticatedUser;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resource-level authorisation for rides. A ride is visible to its passenger, its assigned driver, a
 * driver holding an open offer for it (pending and unexpired), and admins. Everyone else gets 404, so ride ids
 * cannot be probed. A driver whose offer expired, was declined or went to someone else no longer sees the ride:
 * it names the passenger, the places and later the next driver.
 *
 * <p>Methods used before a mutation lock the ride row ({@code SELECT ... FOR UPDATE}). Every ride-mutating
 * transaction therefore locks the ride before touching its offers, so lock order is consistent and
 * concurrent accept/cancel/matching cannot deadlock.
 */
@Component
public class RideAccessPolicy {

    private final RideRepository rides;
    private final RideOfferRepository offers;
    private final Clock clock;

    public RideAccessPolicy(RideRepository rides, RideOfferRepository offers, Clock clock) {
        this.rides = rides;
        this.offers = offers;
        this.clock = clock;
    }

    public Ride loadVisible(AuthenticatedUser user, UUID rideId) {
        Ride ride = rides.findById(rideId).orElseThrow(RideAccessPolicy::notFound);
        boolean visible = switch (user.role()) {
            case ADMIN -> true;
            case PASSENGER -> ride.getPassengerId().equals(user.id());
            case DRIVER -> ride.isAssignedTo(user.id()) || offers.existsByRideIdAndDriverIdAndStatusAndExpiresAtAfter(
                    rideId, user.id(), OfferStatus.PENDING, clock.instant());
        };
        if (!visible) {
            throw notFound();
        }
        return ride;
    }

    /** For accepting an offer: locks the ride; the caller must already hold an offer for it. */
    public Ride lockOfferedTo(UUID driverId, UUID rideId) {
        if (!offers.existsByRideIdAndDriverId(rideId, driverId)) {
            throw notFound();
        }
        return rides.findByIdForUpdate(rideId).orElseThrow(RideAccessPolicy::notFound);
    }

    /** For driver actions: locks the ride, which must be assigned to this driver. */
    public Ride lockAssignedTo(UUID driverId, UUID rideId) {
        return rides.findByIdForUpdate(rideId).filter(ride -> ride.isAssignedTo(driverId))
                .orElseThrow(RideAccessPolicy::notFound);
    }

    /** For cancellation: locks the ride, which must belong to the passenger or be assigned to the driver. */
    public Ride lockOwnedBy(AuthenticatedUser user, UUID rideId) {
        Ride ride = rides.findByIdForUpdate(rideId).orElseThrow(RideAccessPolicy::notFound);
        boolean owner = user.role() == Role.PASSENGER
                ? ride.getPassengerId().equals(user.id())
                : ride.isAssignedTo(user.id());
        if (!owner) {
            throw notFound();
        }
        return ride;
    }

    public static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException(ErrorCode.RIDE_NOT_FOUND, "Ride not found");
    }
}
