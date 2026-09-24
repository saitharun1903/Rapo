package com.rideflow.exception;

/**
 * Base class for expected, client-facing failures. The {@link ErrorCode} determines the HTTP status;
 * the message is safe to show to API clients and must never contain secrets or internal details.
 */
public class RideFlowException extends RuntimeException {

    private final ErrorCode code;

    public RideFlowException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
