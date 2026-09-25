package com.rideflow.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** A passenger's earlier completed trips, aggregated, for comparing one trip with their own history. */
@Repository
public class TripHistoryRepository {

    /**
     * The passenger's most recent completed trips before {@code before}, in the same currency, excluding the trip
     * itself. Served by {@code ix_rides_passenger_requested}.
     */
    private static final String AVERAGES = """
            SELECT count(*) AS trips,
                   avg(f.total) AS avg_fare,
                   avg(f.total / (r.actual_distance_m / 1000.0)) AS avg_fare_per_km,
                   avg(r.surge_multiplier) AS avg_surge
            FROM (
                SELECT id, actual_distance_m, surge_multiplier FROM rides
                WHERE passenger_id = :passengerId AND status = 'COMPLETED' AND id <> :rideId
                  AND completed_at < :before AND currency = :currency AND actual_distance_m > 0
                ORDER BY requested_at DESC
                LIMIT :limit
            ) r
            JOIN fare_breakdowns f ON f.ride_id = r.id AND f.kind = 'FINAL'
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public TripHistoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public History averages(UUID passengerId, UUID rideId, String currency, Instant before, int limit) {
        return jdbc.queryForObject(AVERAGES, new MapSqlParameterSource()
                .addValue("passengerId", passengerId)
                .addValue("rideId", rideId)
                .addValue("currency", currency)
                .addValue("before", SqlTime.utc(before))
                .addValue("limit", limit), (rs, row) -> new History(
                rs.getInt("trips"),
                rs.getBigDecimal("avg_fare"),
                rs.getBigDecimal("avg_fare_per_km"),
                rs.getBigDecimal("avg_surge")));
    }

    /** Averages are {@code null} when {@code trips} is 0. */
    public record History(int trips, BigDecimal avgFare, BigDecimal avgFarePerKm, BigDecimal avgSurge) {
    }
}
