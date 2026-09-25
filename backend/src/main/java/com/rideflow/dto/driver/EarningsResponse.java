package com.rideflow.dto.driver;

import com.rideflow.dto.common.Money;
import com.rideflow.dto.common.ReportGranularity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * A driver's earnings from captured payments of rides completed in {@code [from, to)}: their share after the
 * platform fee. {@code series} has one entry per bucket, empty ones included, cut in {@code timeZone}.
 */
public record EarningsResponse(
        Instant from,
        Instant to,
        ReportGranularity granularity,
        String timeZone,
        Money total,
        int tripCount,
        List<Bucket> series) {

    @Schema(name = "EarningsBucket")
    public record Bucket(Instant start, Money earnings, int trips) {
    }
}
