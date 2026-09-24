package com.rideflow.service.rating;

import com.rideflow.dto.rating.RateRideRequest;
import com.rideflow.dto.rating.RatingResponse;
import com.rideflow.entity.Driver;
import com.rideflow.entity.Rating;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import com.rideflow.entity.Role;
import com.rideflow.exception.DuplicateResourceException;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.RatingRepository;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.ride.RideAccessPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ratings after a completed ride: the passenger rates the driver and the driver rates the passenger, once
 * each. A driver's average is recomputed from all their ratings under the driver row lock, so concurrent
 * ratings cannot lose an update and rounding never accumulates.
 */
@Service
public class RatingService {

    /** Matches {@code drivers.rating_avg numeric(3,2)}. */
    private static final int AVERAGE_SCALE = 2;

    private final RideAccessPolicy access;
    private final RatingRepository ratings;
    private final DriverRepository drivers;

    public RatingService(RideAccessPolicy access, RatingRepository ratings, DriverRepository drivers) {
        this.access = access;
        this.ratings = ratings;
        this.drivers = drivers;
    }

    @Transactional
    public RatingResponse rate(AuthenticatedUser user, UUID rideId, RateRideRequest request) {
        Ride ride = access.loadVisible(user, rideId);
        boolean passengerRatesDriver = user.role() == Role.PASSENGER && ride.getPassengerId().equals(user.id());
        boolean driverRatesPassenger = user.role() == Role.DRIVER && ride.isAssignedTo(user.id());
        if (!passengerRatesDriver && !driverRatesPassenger) {
            // A driver who was only offered the ride can see it, but did not take part in it.
            throw RideAccessPolicy.notFound();
        }
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new InvalidStateException(ErrorCode.RIDE_NOT_COMPLETED, "Only completed rides can be rated");
        }
        if (ratings.existsByRideIdAndRaterId(rideId, user.id())) {
            throw alreadyRated();
        }
        UUID rateeId = passengerRatesDriver ? ride.getDriverId() : ride.getPassengerId();
        Driver ratedDriver = passengerRatesDriver ? drivers.findByIdForUpdate(rateeId)
                .orElseThrow(() -> new IllegalStateException("Completed ride " + rideId + " has no driver profile"))
                : null;
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        Rating rating;
        try {
            rating = ratings.saveAndFlush(Rating.of(rideId, user.id(), rateeId, request.score(), comment));
        } catch (DataIntegrityViolationException ex) {
            // ux_ratings_ride_rater: a concurrent duplicate submission won.
            throw alreadyRated();
        }
        if (ratedDriver != null) {
            RatingRepository.RatingStats stats = ratings.statsFor(rateeId);
            ratedDriver.updateRating(BigDecimal.valueOf(stats.getAverage()).setScale(AVERAGE_SCALE, RoundingMode.HALF_UP),
                    Math.toIntExact(stats.getCount()));
        }
        return new RatingResponse(rating.getId(), rideId, rating.getScore(), rating.getComment(), rating.getCreatedAt());
    }

    private static DuplicateResourceException alreadyRated() {
        return new DuplicateResourceException(ErrorCode.ALREADY_RATED, "You have already rated this ride");
    }
}
