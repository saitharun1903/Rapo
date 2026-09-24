package com.rideflow.service.matching;

import com.rideflow.dto.ride.RideOfferResponse;
import com.rideflow.dto.ride.RideResponse;
import com.rideflow.entity.OfferStatus;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideOffer;
import com.rideflow.repository.RideOfferRepository;
import com.rideflow.repository.RideRepository;
import com.rideflow.service.ride.RideViewAssembler;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A driver's open offers. Pushed in real time from Phase 4; this is also the reconnect snapshot. */
@Service
@Transactional(readOnly = true)
public class OfferQueryService {

    private final RideOfferRepository offers;
    private final RideRepository rides;
    private final RideViewAssembler views;
    private final Clock clock;

    public OfferQueryService(RideOfferRepository offers, RideRepository rides, RideViewAssembler views, Clock clock) {
        this.offers = offers;
        this.rides = rides;
        this.views = views;
        this.clock = clock;
    }

    public List<RideOfferResponse> openOffers(UUID driverId) {
        return offers.findByDriverIdAndStatusAndExpiresAtAfterOrderByOfferedAtDesc(driverId, OfferStatus.PENDING, clock.instant())
                .stream()
                .flatMap(offer -> rides.findById(offer.getRideId()).map(ride -> toResponse(offer, ride)).stream())
                .toList();
    }

    private RideOfferResponse toResponse(RideOffer offer, Ride ride) {
        return new RideOfferResponse(offer.getId(), ride.getId(), offer.getRound(), offer.getDistanceMeters(),
                new RideResponse.Place(ride.getPickup(), ride.getPickupAddress()),
                new RideResponse.Place(ride.getDropoff(), ride.getDropoffAddress()),
                ride.getVehicleCategory(), views.estimatedFare(ride), ride.getEstimatedDistanceMeters(),
                ride.getEstimatedDurationSeconds(), offer.getExpiresAt());
    }
}
