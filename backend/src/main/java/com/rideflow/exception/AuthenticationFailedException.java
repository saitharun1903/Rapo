package com.rideflow.exception;

/** Login or token-refresh failure. Messages are deliberately generic to avoid account enumeration. */
public class AuthenticationFailedException extends RideFlowException {

    public AuthenticationFailedException(ErrorCode code, String message) {
        super(code, message);
    }
}
