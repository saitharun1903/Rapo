package com.rideflow.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import io.sentry.Sentry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The enabled path, which needs a Sentry DSN and the logback appender, is covered by ErrorReportingIT. */
class ErrorReportingServiceTest {

    @Test
    void refusesWhenSentryIsOff() {
        Sentry.close();

        assertThatThrownBy(() -> new ErrorReportingService().sendTestError(UUID.randomUUID()))
                .isInstanceOfSatisfying(InvalidStateException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ERROR_REPORTING_DISABLED));
    }
}
