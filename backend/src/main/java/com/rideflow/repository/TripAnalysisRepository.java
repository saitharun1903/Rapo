package com.rideflow.repository;

import com.rideflow.entity.TripAnalysisStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code trip_analyses}: one AI analysis per completed ride, with its exact input. {@code attempts} counts runs
 * (the first one and each regeneration). JDBC because of the jsonb columns.
 */
@Repository
public class TripAnalysisRepository {

    private static final String COLUMNS = """
            ride_id, status, failure_code, provider, model, prompt_version, facts::text AS facts,
            observations::text AS observations, result::text AS result, latency_ms, input_tokens, output_tokens,
            attempts, updated_at
            """;

    private static final String FIND = "SELECT " + COLUMNS + " FROM trip_analyses WHERE ride_id = :rideId";

    /** The unique ride_id makes a second insert (redelivery, a regenerate racing the consumer) a no-op. */
    private static final String INSERT_PENDING = """
            INSERT INTO trip_analyses (ride_id, status, prompt_version, facts, observations, created_at, updated_at)
            VALUES (:rideId, 'PENDING', :promptVersion, CAST(:facts AS jsonb), CAST(:observations AS jsonb), :now, :now)
            ON CONFLICT (ride_id) DO NOTHING
            """;

    private static final String COMPLETE = """
            UPDATE trip_analyses
            SET status = 'COMPLETED', failure_code = NULL, provider = :provider, model = :model,
                result = CAST(:result AS jsonb), latency_ms = :latencyMs, input_tokens = :inputTokens,
                output_tokens = :outputTokens, attempts = attempts + 1, updated_at = :now
            WHERE ride_id = :rideId AND status = 'PENDING'
            """;

    private static final String FAIL = """
            UPDATE trip_analyses
            SET status = :status, failure_code = :failureCode, provider = :provider, model = :model,
                latency_ms = :latencyMs, attempts = attempts + 1, updated_at = :now
            WHERE ride_id = :rideId AND status = 'PENDING'
            """;

    /** Claims a failed analysis, or one whose run was abandoned, for another run; only one caller wins. */
    private static final String CLAIM_FOR_RETRY = """
            UPDATE trip_analyses
            SET status = 'PENDING', failure_code = NULL, updated_at = :now
            WHERE ride_id = :rideId
              AND (status IN ('FAILED', 'UNAVAILABLE') OR (status = 'PENDING' AND updated_at < :staleBefore))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public TripAnalysisRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TripAnalysisRow> findByRideId(UUID rideId) {
        return jdbc.query(FIND, new MapSqlParameterSource("rideId", rideId), (rs, row) -> new TripAnalysisRow(
                        rs.getObject("ride_id", UUID.class),
                        TripAnalysisStatus.valueOf(rs.getString("status")),
                        rs.getString("failure_code"),
                        rs.getString("provider"),
                        rs.getString("model"),
                        rs.getString("prompt_version"),
                        rs.getString("facts"),
                        rs.getString("observations"),
                        rs.getString("result"),
                        (Integer) rs.getObject("latency_ms"),
                        (Integer) rs.getObject("input_tokens"),
                        (Integer) rs.getObject("output_tokens"),
                        rs.getInt("attempts"),
                        SqlTime.instant(rs.getTimestamp("updated_at"))))
                .stream().findFirst();
    }

    /** @return {@code true} if a new PENDING analysis was created */
    public boolean insertPending(UUID rideId, String promptVersion, String factsJson, String observationsJson,
                                 Instant now) {
        return jdbc.update(INSERT_PENDING, new MapSqlParameterSource()
                .addValue("rideId", rideId)
                .addValue("promptVersion", promptVersion)
                .addValue("facts", factsJson)
                .addValue("observations", observationsJson)
                .addValue("now", SqlTime.utc(now))) == 1;
    }

    public void markCompleted(UUID rideId, Outcome outcome, String resultJson, long inputTokens, long outputTokens,
                              Instant now) {
        jdbc.update(COMPLETE, outcome.params(rideId, now)
                .addValue("result", resultJson)
                .addValue("inputTokens", Math.toIntExact(inputTokens))
                .addValue("outputTokens", Math.toIntExact(outputTokens)));
    }

    public void markFailed(UUID rideId, TripAnalysisStatus status, String failureCode, Outcome outcome, Instant now) {
        jdbc.update(FAIL, outcome.params(rideId, now)
                .addValue("status", status.name())
                .addValue("failureCode", failureCode));
    }

    /** @return {@code true} if this caller claimed the analysis for a new run */
    public boolean claimForRetry(UUID rideId, Instant staleBefore, Instant now) {
        return jdbc.update(CLAIM_FOR_RETRY, new MapSqlParameterSource()
                .addValue("rideId", rideId)
                .addValue("staleBefore", SqlTime.utc(staleBefore))
                .addValue("now", SqlTime.utc(now))) == 1;
    }

    /** Who answered and how long it took, for completed and failed runs alike. */
    public record Outcome(String provider, String model, long latencyMs) {

        MapSqlParameterSource params(UUID rideId, Instant now) {
            return new MapSqlParameterSource()
                    .addValue("rideId", rideId)
                    .addValue("provider", provider)
                    .addValue("model", model)
                    .addValue("latencyMs", Math.toIntExact(latencyMs))
                    .addValue("now", SqlTime.utc(now));
        }
    }

    public record TripAnalysisRow(
            UUID rideId,
            TripAnalysisStatus status,
            String failureCode,
            String provider,
            String model,
            String promptVersion,
            String factsJson,
            String observationsJson,
            String resultJson,
            Integer latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            int attempts,
            Instant updatedAt) {
    }
}
