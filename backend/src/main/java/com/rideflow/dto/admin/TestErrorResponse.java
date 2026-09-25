package com.rideflow.dto.admin;

/** @param eventId the Sentry event id of the test error, to search for in Sentry */
public record TestErrorResponse(String eventId) {
}
