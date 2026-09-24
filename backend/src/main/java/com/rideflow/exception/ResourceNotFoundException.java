package com.rideflow.exception;

public class ResourceNotFoundException extends RideFlowException {

    public ResourceNotFoundException(ErrorCode code, String message) {
        super(code, message);
    }
}
