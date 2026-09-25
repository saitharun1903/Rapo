package com.rideflow.dto.ai;

/** A statement computed by the backend from the trip's facts (not AI output). */
public record TripObservationResponse(String key, String text) {
}
