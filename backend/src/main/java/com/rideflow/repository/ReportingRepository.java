package com.rideflow.repository;

import com.rideflow.entity.DriverAvailability;
import com.rideflow.entity.RideStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Aggregates for driver earnings and the admin console. Time series come back gap-free: one row per bucket in
 * {@code [from, to)}, cut at local hour or day boundaries of the given zone, including empty buckets.
 * Only captured payments count as money.
 */
@Repository
public class ReportingRepository {

    /** Every bucket start in the window, as local time in the zone; the last one starts before {@code to}. */
    private static final String BUCKETS = """
            buckets AS (
                SELECT generate_series(
                           date_trunc(CAST(:unit AS text), CAST(:from AS timestamptz) AT TIME ZONE CAST(:zone AS text)),
                           CAST(:to AS timestamptz) AT TIME ZONE CAST(:zone AS text) - interval '1 microsecond',
                           CAST('1 ' || CAST(:unit AS text) AS interval)) AS bucket
            )
            """;

    private static final String DRIVER_EARNINGS = "WITH " + BUCKETS + """
            , trips AS (
                SELECT date_trunc(CAST(:unit AS text), r.completed_at AT TIME ZONE CAST(:zone AS text)) AS bucket,
                       p.driver_earnings
                FROM rides r
                JOIN payments p ON p.ride_id = r.id AND p.status = 'CAPTURED'
                WHERE r.driver_id = :driverId AND r.status = 'COMPLETED' AND r.currency = :currency
                  AND r.completed_at >= :from AND r.completed_at < :to
            )
            SELECT b.bucket AT TIME ZONE CAST(:zone AS text) AS bucket_start,
                   coalesce(sum(t.driver_earnings), 0) AS amount,
                   count(t.bucket) AS trips
            FROM buckets b
            LEFT JOIN trips t ON t.bucket = b.bucket
            GROUP BY b.bucket
            ORDER BY b.bucket
            """;

    /**
     * Each ride contributes to the bucket of every event in the window: its request, and its completion,
     * cancellation or expiry. Revenue is the captured amount, in the bucket of the completion.
     */
    private static final String RIDE_ACTIVITY = "WITH " + BUCKETS + """
            , events AS (
                SELECT requested_at AS at, 1 AS requested, 0 AS completed, 0 AS cancelled, 0 AS expired,
                       CAST(0 AS numeric) AS revenue
                FROM rides WHERE requested_at >= :from AND requested_at < :to
                UNION ALL
                SELECT r.completed_at, 0, 1, 0, 0, coalesce(p.amount, 0)
                FROM rides r
                LEFT JOIN payments p ON p.ride_id = r.id AND p.status = 'CAPTURED' AND r.currency = :currency
                WHERE r.status = 'COMPLETED' AND r.completed_at >= :from AND r.completed_at < :to
                UNION ALL
                SELECT cancelled_at, 0, 0, 1, 0, 0
                FROM rides WHERE status = 'CANCELLED' AND cancelled_at >= :from AND cancelled_at < :to
                UNION ALL
                SELECT expired_at, 0, 0, 0, 1, 0
                FROM rides WHERE status = 'EXPIRED' AND expired_at >= :from AND expired_at < :to
            )
            SELECT b.bucket AT TIME ZONE CAST(:zone AS text) AS bucket_start,
                   coalesce(sum(e.requested), 0) AS requested,
                   coalesce(sum(e.completed), 0) AS completed,
                   coalesce(sum(e.cancelled), 0) AS cancelled,
                   coalesce(sum(e.expired), 0) AS expired,
                   coalesce(sum(e.revenue), 0) AS revenue
            FROM buckets b
            LEFT JOIN events e
                   ON date_trunc(CAST(:unit AS text), e.at AT TIME ZONE CAST(:zone AS text)) = b.bucket
            GROUP BY b.bucket
            ORDER BY b.bucket
            """;

    private static final String STATUS_COUNTS = """
            SELECT status, count(*) AS rides FROM rides
            WHERE requested_at >= :from AND requested_at < :to
            GROUP BY status
            """;

    private static final String MEDIAN_SECONDS_TO_MATCH = """
            SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM accepted_at - requested_at))
            FROM rides
            WHERE requested_at >= :from AND requested_at < :to AND accepted_at IS NOT NULL
            """;

    private static final String REVENUE = """
            SELECT coalesce(sum(p.amount), 0) AS gross, coalesce(sum(p.platform_fee), 0) AS platform_fees
            FROM rides r
            JOIN payments p ON p.ride_id = r.id AND p.status = 'CAPTURED'
            WHERE r.status = 'COMPLETED' AND r.currency = :currency
              AND r.completed_at >= :from AND r.completed_at < :to
            """;

    private static final String VERIFIED_DRIVERS_BY_AVAILABILITY = """
            SELECT availability, count(*) AS drivers FROM drivers
            WHERE verification_status = 'VERIFIED'
            GROUP BY availability
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ReportingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EarningsBucket> driverEarnings(UUID driverId, String currency, Window window) {
        return jdbc.query(DRIVER_EARNINGS, window.parameters().addValue("driverId", driverId)
                .addValue("currency", currency), (rs, row) -> new EarningsBucket(
                bucketStart(rs), rs.getBigDecimal("amount"), rs.getInt("trips")));
    }

    public List<ActivityBucket> rideActivity(String currency, Window window) {
        return jdbc.query(RIDE_ACTIVITY, window.parameters().addValue("currency", currency),
                (rs, row) -> new ActivityBucket(bucketStart(rs), rs.getInt("requested"), rs.getInt("completed"),
                        rs.getInt("cancelled"), rs.getInt("expired"), rs.getBigDecimal("revenue")));
    }

    /** Rides requested in the window, by their current status; statuses with no rides are absent. */
    public Map<RideStatus, Long> ridesByStatus(Instant from, Instant to) {
        Map<RideStatus, Long> counts = new EnumMap<>(RideStatus.class);
        jdbc.query(STATUS_COUNTS, range(from, to), rs -> {
            counts.put(RideStatus.valueOf(rs.getString("status")), rs.getLong("rides"));
        });
        return counts;
    }

    /** {@code null} when no ride requested in the window was accepted. */
    public Double medianSecondsToMatch(Instant from, Instant to) {
        return jdbc.queryForObject(MEDIAN_SECONDS_TO_MATCH, range(from, to), Double.class);
    }

    public Revenue revenue(String currency, Instant from, Instant to) {
        return jdbc.queryForObject(REVENUE, range(from, to).addValue("currency", currency),
                (rs, row) -> new Revenue(rs.getBigDecimal("gross"), rs.getBigDecimal("platform_fees")));
    }

    /** Verified drivers by availability; availabilities with no drivers are absent. */
    public Map<DriverAvailability, Long> verifiedDriversByAvailability() {
        Map<DriverAvailability, Long> counts = new EnumMap<>(DriverAvailability.class);
        jdbc.query(VERIFIED_DRIVERS_BY_AVAILABILITY, rs -> {
            counts.put(DriverAvailability.valueOf(rs.getString("availability")), rs.getLong("drivers"));
        });
        return counts;
    }

    private static MapSqlParameterSource range(Instant from, Instant to) {
        return new MapSqlParameterSource()
                .addValue("from", SqlTime.utc(from))
                .addValue("to", SqlTime.utc(to));
    }

    private static Instant bucketStart(ResultSet rs) throws SQLException {
        return SqlTime.instant(rs.getTimestamp("bucket_start"));
    }

    /**
     * A reporting window for a time series.
     *
     * @param unit PostgreSQL {@code date_trunc} unit of one bucket ({@code hour} or {@code day})
     */
    public record Window(Instant from, Instant to, String unit, ZoneId zone) {

        MapSqlParameterSource parameters() {
            return range(from, to)
                    .addValue("unit", unit)
                    .addValue("zone", zone.getId());
        }
    }

    public record EarningsBucket(Instant start, BigDecimal amount, int trips) {
    }

    public record ActivityBucket(Instant start, int requested, int completed, int cancelled, int expired,
                                 BigDecimal revenue) {
    }

    public record Revenue(BigDecimal gross, BigDecimal platformFees) {
    }
}
