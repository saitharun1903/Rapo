package com.rideflow.service.admin;

import com.rideflow.config.PricingProperties;
import com.rideflow.config.ReportingProperties;
import com.rideflow.dto.admin.AdminOverviewResponse;
import com.rideflow.dto.admin.RideActivityResponse;
import com.rideflow.dto.common.Money;
import com.rideflow.dto.common.ReportGranularity;
import com.rideflow.entity.DriverAvailability;
import com.rideflow.entity.RideStatus;
import com.rideflow.repository.ReportingRepository;
import com.rideflow.service.report.ReportRange;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform-wide numbers for the admin console, computed from the database on each request. */
@Service
@Transactional(readOnly = true)
public class AdminReportService {

    private final ReportingRepository reports;
    private final ReportingProperties settings;
    private final PricingProperties pricing;

    public AdminReportService(ReportingRepository reports, ReportingProperties settings, PricingProperties pricing) {
        this.reports = reports;
        this.settings = settings;
        this.pricing = pricing;
    }

    public AdminOverviewResponse overview(Instant from, Instant to) {
        // Validates the window; the overview itself is not bucketed.
        ReportRange range = ReportRange.of(from, to, ReportGranularity.DAY, settings);
        Map<RideStatus, Long> byStatus = withZeros(RideStatus.class, reports.ridesByStatus(range.from(), range.to()));
        long requested = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long completed = byStatus.get(RideStatus.COMPLETED);
        long cancelled = byStatus.get(RideStatus.CANCELLED);
        long finished = completed + cancelled + byStatus.get(RideStatus.EXPIRED);
        ReportingRepository.Revenue revenue = reports.revenue(pricing.currency(), range.from(), range.to());
        return new AdminOverviewResponse(range.from(), range.to(), requested, byStatus,
                share(completed, finished), share(cancelled, finished),
                reports.medianSecondsToMatch(range.from(), range.to()),
                Money.of(revenue.gross(), pricing.currency()),
                Money.of(revenue.platformFees(), pricing.currency()),
                withZeros(DriverAvailability.class, reports.verifiedDriversByAvailability()));
    }

    public RideActivityResponse rideActivity(Instant from, Instant to, ReportGranularity granularity) {
        ReportRange range = ReportRange.of(from, to, granularity, settings);
        String currency = pricing.currency();
        return new RideActivityResponse(range.from(), range.to(), granularity, range.zone().getId(),
                reports.rideActivity(currency, range.window()).stream()
                        .map(bucket -> new RideActivityResponse.Bucket(bucket.start(), bucket.requested(),
                                bucket.completed(), bucket.cancelled(), bucket.expired(),
                                Money.of(bucket.revenue(), currency)))
                        .toList());
    }

    private static Double share(long part, long whole) {
        return whole == 0 ? null : (double) part / whole;
    }

    /** Every constant of the enum, so clients never have to treat a missing key as zero. */
    private static <E extends Enum<E>> Map<E, Long> withZeros(Class<E> type, Map<E, Long> counts) {
        Map<E, Long> complete = new EnumMap<>(type);
        Arrays.stream(type.getEnumConstants()).forEach(constant -> complete.put(constant, counts.getOrDefault(constant, 0L)));
        return complete;
    }
}
