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
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A driver's open offers. New offers are pushed over WebSocket; this is the reconnect snapshot. */
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

    /** The driver's offer for this ride if it is still open; used to push a new offer to the driver. */
    public Optional<RideOfferResponse> openOffer(UUID driverId, UUID rideId) {
        return offers.findByRideIdAndDriverId(rideId, driverId)
                .filter(offer -> offer.getStatus() == OfferStatus.PENDING && offer.getExpiresAt().isAfter(clock.instant()))
                .flatMap(offer -> rides.findById(rideId).map(ride -> toResponse(offer, ride)));
    }

    private RideOfferResponse toResponse(RideOffer offer, Ride ride) {
        return new RideOfferResponse(offer.getId(), ride.getId(), offer.getRound(), offer.getDistanceMeters(),
                new RideResponse.Place(ride.getPickup(), ride.getPickupAddress()),
                new RideResponse.Place(ride.getDropoff(), ride.getDropoffAddress()),
                ride.getVehicleCategory(), views.estimatedFare(ride), ride.getEstimatedDistanceMeters(),
                ride.getEstimatedDurationSeconds(), offer.getExpiresAt());
    }
}
