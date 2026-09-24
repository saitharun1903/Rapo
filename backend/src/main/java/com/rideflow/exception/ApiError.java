package com.rideflow.exception;

import java.time.Instant;
import java.util.List;

/** The single error shape returned by every endpoint. See docs/architecture.md section 14. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }
}
