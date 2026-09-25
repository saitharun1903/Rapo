package com.rideflow.dto.common;

import java.time.Duration;
import java.util.Locale;

/** Width of one bucket in a time series; {@link #sqlUnit()} is the matching PostgreSQL {@code date_trunc} unit. */
public enum ReportGranularity {
    HOUR(Duration.ofHours(1)),
    DAY(Duration.ofDays(1));

    private final Duration width;

    ReportGranularity(Duration width) {
        this.width = width;
    }

    public Duration width() {
        return width;
    }

    public String sqlUnit() {
        return name().toLowerCase(Locale.ROOT);
    }
}
