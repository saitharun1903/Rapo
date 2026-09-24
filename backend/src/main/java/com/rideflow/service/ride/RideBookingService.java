package com.rideflow.service.ride;

import com.rideflow.cache.RateLimitScope;
import com.rideflow.cache.RateLimiter;
import com.rideflow.dto.ride.BookRideRequest;
import com.rideflow.dto.ride.RideResponse;
import com.rideflow.entity.ActorType;
import com.rideflow.entity.FareBreakdown;
import com.rideflow.entity.FareKind;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import com.rideflow.repository.FareBreakdownRepository;
import com.rideflow.repository.RideRepository;
import com.rideflow.service.event.DomainEventPublisher;
import com.rideflow.service.fare.FareQuote;
import com.rideflow.service.fare.FareQuoteService;
import com.rideflow.service.ride.event.MatchingRoundRequestedEvent;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a verified fare quote into a REQUESTED ride. Matching happens asynchronously after commit, so
 * the passenger gets 201 immediately and follows progress through status updates.
 */
@Service
public class RideBookingService {

    private static final Logger log = LoggerFactory.getLogger(RideBookingService.class);

    private final FareQuoteService quotes;
    private final RideRepository rides;
    private final FareBreakdownRepository fareBreakdowns;
    private final RideTransitionRecorder recorder;
    private final DomainEventPublisher events;
    private final RideViewAssembler views;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public RideBookingService(FareQuoteService quotes, RideRepository rides, FareBreakdownRepository fareBreakdowns,
                              RideTransitionRecorder recorder, DomainEventPublisher events, RideViewAssembler views,
                              RateLimiter rateLimiter, Clock clock) {
        this.quotes = quotes;
        this.rides = rides;
        this.fareBreakdowns = fareBreakdowns;
        this.recorder = recorder;
        this.events = events;
        this.views = views;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional
    public RideResponse book(UUID passengerId, BookRideRequest request) {
        rateLimiter.acquire(RateLimitScope.RIDE_BOOKING, passengerId.toString());
        FareQuote quote = quotes.redeem(passengerId, request.quoteId(), request.pickup().point(), request.dropoff().point());
        if (rides.findFirstByPassengerIdAndStatusIn(passengerId, RideStatus.ACTIVE).isPresent()) {
            throw activeRideExists();
        }
        // The priced coordinates from the quote are authoritative; addresses are display labels.
        Ride ride = Ride.request(new Ride.RequestDetails(
                passengerId, quote.category(),
                quote.pickup(), request.pickup().address().trim(),
                quote.dropoff(), request.dropoff().address().trim(),
                request.paymentMethod(),
                quote.distanceMeters(), quote.durationSeconds(), quote.estimateSource(),
                quote.fare().surgeMultiplier(), quote.fare().currency()), clock.instant());
        try {
            ride = recorder.record(ride, new Ride.StatusChange(null, RideStatus.REQUESTED), ActorType.PASSENGER,
                    passengerId, null);
        } catch (DataIntegrityViolationException ex) {
            // ux_rides_passenger_active: a concurrent request from the same passenger won the race.
            throw activeRideExists();
        }
        fareBreakdowns.save(FareBreakdown.of(ride.getId(), FareKind.ESTIMATE, quote.fare()));
        events.publish(new MatchingRoundRequestedEvent(ride.getId()));
        log.info("Ride {} requested ({}, {} m, fare {} {})", ride.getId(), ride.getVehicleCategory(),
                ride.getEstimatedDistanceMeters(), quote.fare().total(), quote.fare().currency());
        return views.toResponse(ride);
    }

    private static InvalidStateException activeRideExists() {
        return new InvalidStateException(ErrorCode.ACTIVE_RIDE_EXISTS, "You already have an active ride");
    }
}
