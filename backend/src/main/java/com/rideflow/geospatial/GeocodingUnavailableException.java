package com.rideflow.geospatial;

/** The geocoding provider failed or is not configured; the message is for logs, not clients. */
public class GeocodingUnavailableException extends RuntimeException {

    public GeocodingUnavailableException(String message) {
        super(message);
    }

    public GeocodingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
