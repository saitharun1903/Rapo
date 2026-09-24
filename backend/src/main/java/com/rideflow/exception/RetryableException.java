package com.rideflow.exception;

import java.time.Duration;

/** A failure the client may retry after a delay; sent with a {@code Retry-After} header. */
public class RetryableException extends RideFlowException {

    private final Duration retryAfter;

    public RetryableException(ErrorCode code, String message, Duration retryAfter) {
        super(code, message);
        this.retryAfter = retryAfter;
    }

    /** Whole seconds, rounded up and at least one, as {@code Retry-After} requires. */
    public long retryAfterSeconds() {
        long millis = Math.max(retryAfter.toMillis(), 1);
        return (millis + 999) / 1000;
    }
}
