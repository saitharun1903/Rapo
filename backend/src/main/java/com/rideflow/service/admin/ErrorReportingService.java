package com.rideflow.service.admin;

import com.rideflow.dto.admin.TestErrorResponse;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import io.sentry.Sentry;
import io.sentry.protocol.SentryId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lets an admin confirm that errors reach Sentry. The test error takes the same path as a real one: an ERROR log
 * event, which the Sentry logback appender captures on this thread, so its event id can be handed back.
 */
@Service
public class ErrorReportingService {

    private static final Logger log = LoggerFactory.getLogger(ErrorReportingService.class);

    public TestErrorResponse sendTestError(UUID adminId) {
        if (!Sentry.isEnabled()) {
            throw new InvalidStateException(ErrorCode.ERROR_REPORTING_DISABLED,
                    "Error reporting is off; set SENTRY_DSN to turn it on");
        }
        log.error("Test error requested from the admin console by {}", adminId, new TestError());
        SentryId eventId = Sentry.getLastEventId();
        if (SentryId.EMPTY_ID.equals(eventId)) {
            throw new IllegalStateException("Sentry is enabled but did not capture the test error");
        }
        return new TestErrorResponse(eventId.toString());
    }

    /** Its own type, so the issue is recognisable in Sentry and can be resolved without a second look. */
    static final class TestError extends RuntimeException {

        TestError() {
            super("Deliberate test error from the RideFlow admin console; safe to resolve");
        }
    }
}
