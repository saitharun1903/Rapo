package com.rideflow.geospatial;

/** The routing provider failed (timeout, HTTP error, no route). Handled by falling back, never shown to clients. */
public class RoutingUnavailableException extends RuntimeException {

    public RoutingUnavailableException(String message) {
        super(message);
    }

    public RoutingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
