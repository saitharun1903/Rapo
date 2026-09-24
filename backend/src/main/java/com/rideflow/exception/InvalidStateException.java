package com.rideflow.exception;

/** An operation is not allowed in the entity's current state. */
public class InvalidStateException extends RideFlowException {

    public InvalidStateException(ErrorCode code, String message) {
        super(code, message);
    }
}
