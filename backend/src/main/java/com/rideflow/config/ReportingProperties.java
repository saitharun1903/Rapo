package com.rideflow.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param timeZone   zone in which hour and day buckets of earnings and admin analytics are cut
 * @param maxBuckets most buckets one report may span, which bounds the size of its query
 */
@Validated
@ConfigurationProperties("rideflow.reporting")
public record ReportingProperties(@NotNull ZoneId timeZone, @Min(1) int maxBuckets) {
}
