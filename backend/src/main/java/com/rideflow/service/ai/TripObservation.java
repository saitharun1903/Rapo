package com.rideflow.service.ai;

/**
 * A statement about a trip computed by the backend from its facts, not by AI. Always shown, whatever the AI
 * status, and labelled as computed.
 */
public record TripObservation(String key, String text) {
}
