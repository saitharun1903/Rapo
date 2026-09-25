package com.rideflow.service.driver;

import com.rideflow.config.PricingProperties;
import com.rideflow.config.ReportingProperties;
import com.rideflow.dto.common.Money;
import com.rideflow.dto.common.ReportGranularity;
import com.rideflow.dto.driver.EarningsResponse;
import com.rideflow.repository.ReportingRepository;
import com.rideflow.repository.ReportingRepository.EarningsBucket;
import com.rideflow.service.report.ReportRange;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A driver's own earnings, from the payments settled for the rides they completed. */
@Service
@Transactional(readOnly = true)
public class DriverEarningsService {

    private final ReportingRepository reports;
    private final ReportingProperties settings;
    private final PricingProperties pricing;

    public DriverEarningsService(ReportingRepository reports, ReportingProperties settings, PricingProperties pricing) {
        this.reports = reports;
        this.settings = settings;
        this.pricing = pricing;
    }

    public EarningsResponse earnings(UUID driverId, Instant from, Instant to, ReportGranularity granularity) {
        ReportRange range = ReportRange.of(from, to, granularity, settings);
        String currency = pricing.currency();
        List<EarningsBucket> buckets = reports.driverEarnings(driverId, currency, range.window());
        BigDecimal total = buckets.stream().map(EarningsBucket::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        int trips = buckets.stream().mapToInt(EarningsBucket::trips).sum();
        return new EarningsResponse(range.from(), range.to(), granularity, range.zone().getId(),
                Money.of(total, currency), trips,
                buckets.stream()
                        .map(bucket -> new EarningsResponse.Bucket(bucket.start(), Money.of(bucket.amount(), currency),
                                bucket.trips()))
                        .toList());
    }
}
