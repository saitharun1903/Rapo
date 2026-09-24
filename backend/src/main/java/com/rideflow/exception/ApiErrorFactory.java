package com.rideflow.exception;

import com.rideflow.monitoring.RequestIdFilter;
import java.time.Clock;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Builds {@link ApiError} bodies for both MVC exception handlers and security filters. */
@Component
public class ApiErrorFactory {

    private final Clock clock;

    public ApiErrorFactory(Clock clock) {
        this.clock = clock;
    }

    public ApiError create(ErrorCode code, String message, String path) {
        return create(code, message, path, List.of());
    }

    public ApiError create(ErrorCode code, String message, String path, List<ApiError.FieldViolation> fieldErrors) {
        return new ApiError(
                clock.instant(),
                code.status().value(),
                code.status().name(),
                code.name(),
                message,
                path,
                MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY),
                fieldErrors);
    }
}
