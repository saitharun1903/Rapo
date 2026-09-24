package com.rideflow.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in {@link ApiError#code()}. Clients branch on these,
 * never on messages, so existing values must not be renamed.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_SORT_FIELD(HttpStatus.BAD_REQUEST),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED),

    FORBIDDEN(HttpStatus.FORBIDDEN),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN),
    CSRF_HEADER_MISSING(HttpStatus.FORBIDDEN),

    NOT_FOUND(HttpStatus.NOT_FOUND),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    DRIVER_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    CONFLICT(HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    PHONE_TAKEN(HttpStatus.CONFLICT),
    LICENSE_TAKEN(HttpStatus.CONFLICT),
    PLATE_TAKEN(HttpStatus.CONFLICT),
    DRIVER_PROFILE_EXISTS(HttpStatus.CONFLICT),
    INVALID_DRIVER_STATE(HttpStatus.CONFLICT),
    INVALID_USER_STATE(HttpStatus.CONFLICT),

    WRONG_CURRENT_PASSWORD(HttpStatus.UNPROCESSABLE_CONTENT),
    INVALID_MODEL_YEAR(HttpStatus.UNPROCESSABLE_CONTENT),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
