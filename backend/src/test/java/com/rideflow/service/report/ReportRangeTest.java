package com.rideflow.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.config.ReportingProperties;
import com.rideflow.dto.common.ReportGranularity;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ReportRangeTest {

    private static final int MAX_BUCKETS = 48;
    private static final ReportingProperties SETTINGS = new ReportingProperties(ZoneId.of("Asia/Kolkata"), MAX_BUCKETS);
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void acceptsAWindowWithinTheBucketLimit() {
        ReportRange range = ReportRange.of(FROM, FROM.plus(Duration.ofHours(MAX_BUCKETS - 1)), ReportGranularity.HOUR,
                SETTINGS);

        assertThat(range.zone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        assertThat(range.window().unit()).isEqualTo("hour");
        assertThat(range.window().zone()).isEqualTo(SETTINGS.timeZone());
    }

    @Test
    void rejectsEmptyAndInvertedWindows() {
        assertThatThrownBy(() -> ReportRange.of(FROM, FROM, ReportGranularity.DAY, SETTINGS))
                .isInstanceOfSatisfying(RideFlowException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));
        assertThatThrownBy(() -> ReportRange.of(FROM, FROM.minusSeconds(1), ReportGranularity.DAY, SETTINGS))
                .isInstanceOf(RideFlowException.class);
    }

    @Test
    void rejectsWindowsNeedingTooManyBuckets() {
        // 48 hours from a whole hour touch 48 buckets, plus the partial one the window may start in.
        assertThatThrownBy(() -> ReportRange.of(FROM, FROM.plus(Duration.ofHours(MAX_BUCKETS)), ReportGranularity.HOUR,
                SETTINGS))
                .isInstanceOfSatisfying(RideFlowException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));
        // The same span is two days.
        assertThat(ReportRange.of(FROM, FROM.plus(Duration.ofHours(MAX_BUCKETS)), ReportGranularity.DAY, SETTINGS)
                .granularity()).isEqualTo(ReportGranularity.DAY);
    }
}
