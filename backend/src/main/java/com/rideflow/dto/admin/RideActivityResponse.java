package com.rideflow.dto.admin;

import com.rideflow.dto.common.Money;
import com.rideflow.dto.common.ReportGranularity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Ride events per bucket in {@code [from, to)}, empty buckets included: requests by request time, and
 * completions, cancellations and expiries by when they happened. Revenue is captured fares, by completion.
 */
public record RideActivityResponse(
        Instant from,
        Instant to,
        ReportGranularity granularity,
        String timeZone,
        List<Bucket> series) {

    @Schema(name = "RideActivityBucket")
    public record Bucket(Instant start, int requested, int completed, int cancelled, int expired, Money revenue) {
    }
}
