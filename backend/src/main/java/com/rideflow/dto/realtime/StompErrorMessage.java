package com.rideflow.dto.realtime;

import com.rideflow.exception.ApiError;
import java.util.List;

/**
 * Error for a rejected client frame, on {@code /user/queue/errors} (session stays open) or in the body of a
 * STOMP ERROR frame (connection closes). {@code code} uses the same vocabulary as {@link ApiError}.
 */
public record StompErrorMessage(String code, String message, String destination,
                                List<ApiError.FieldViolation> fieldErrors) {

    public StompErrorMessage {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
