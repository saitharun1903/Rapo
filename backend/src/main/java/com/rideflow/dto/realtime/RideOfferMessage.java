package com.rideflow.dto.realtime;

import com.rideflow.dto.ride.RideOfferResponse;
import java.util.UUID;

/**
 * Pushed to a driver on {@code /user/queue/ride-offers}. {@code OFFER} carries the offer; {@code WITHDRAWN}
 * means the ride went to someone else or was cancelled, and the driver's app should drop it.
 */
public record RideOfferMessage(Type type, UUID rideId, RideOfferResponse offer) {

    public enum Type {
        OFFER,
        WITHDRAWN
    }

    public static RideOfferMessage offer(RideOfferResponse offer) {
        return new RideOfferMessage(Type.OFFER, offer.rideId(), offer);
    }

    public static RideOfferMessage withdrawn(UUID rideId) {
        return new RideOfferMessage(Type.WITHDRAWN, rideId, null);
    }
}
