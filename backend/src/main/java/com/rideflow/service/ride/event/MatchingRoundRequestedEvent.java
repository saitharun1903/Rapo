package com.rideflow.service.ride.event;

import java.util.UUID;

/** The ride needs a (next) matching round now, instead of waiting for the sweeper. */
public record MatchingRoundRequestedEvent(UUID rideId) {
}
