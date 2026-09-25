package com.rideflow.service.report;

import com.rideflow.config.ReportingProperties;
import com.rideflow.dto.common.ReportGranularity;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import com.rideflow.repository.ReportingRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A validated reporting window {@code [from, to)} with its bucket width and the zone buckets are cut in.
 *
 * @param from        inclusive start
 * @param to          exclusive end
 * @param granularity bucket width
 * @param zone        zone whose local hours and days the buckets follow
 */
public record ReportRange(Instant from, Instant to, ReportGranularity granularity, ZoneId zone) {

    /**
     * Rejects an empty or inverted window, and one that would need more buckets than configured. The first
     * bucket starts at {@code from} truncated to the bucket width, so a window can touch one more bucket
     * than its length alone suggests.
     */
    public static ReportRange of(Instant from, Instant to, ReportGranularity granularity,
                                 ReportingProperties settings) {
        if (!from.isBefore(to)) {
            throw new RideFlowException(ErrorCode.INVALID_DATE_RANGE, "'from' must be before 'to'");
        }
        Duration span = Duration.between(from, to);
        long buckets = span.dividedBy(granularity.width()) + 1;
        if (buckets > settings.maxBuckets()) {
            throw new RideFlowException(ErrorCode.INVALID_DATE_RANGE, "The range spans " + buckets + " "
                    + granularity.sqlUnit() + " buckets; at most " + settings.maxBuckets() + " are allowed");
        }
        return new ReportRange(from, to, granularity, settings.timeZone());
    }

    public ReportingRepository.Window window() {
        return new ReportingRepository.Window(from, to, granularity.sqlUnit(), zone);
    }
}
