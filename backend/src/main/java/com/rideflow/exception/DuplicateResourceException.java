package com.rideflow.exception;

public class DuplicateResourceException extends RideFlowException {

    public DuplicateResourceException(ErrorCode code, String message) {
        super(code, message);
    }
}
