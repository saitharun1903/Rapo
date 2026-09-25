package com.rideflow.repository;

import com.rideflow.entity.TripQuestionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code trip_questions}: every question asked about a trip and what came back, including failures. */
@Repository
public class TripQuestionRepository {

    private static final String INSERT = """
            INSERT INTO trip_questions (id, ride_id, user_id, question, status, failure_code, answer, provider, model,
                                        prompt_version, latency_ms, created_at)
            VALUES (:id, :rideId, :userId, :question, :status, :failureCode, CAST(:answer AS jsonb), :provider, :model,
                    :promptVersion, :latencyMs, :createdAt)
            """;

    private static final String LIST = """
            SELECT id, question, status, failure_code, answer::text AS answer, created_at
            FROM trip_questions WHERE ride_id = :rideId AND user_id = :userId
            ORDER BY created_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public TripQuestionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(NewQuestion question) {
        jdbc.update(INSERT, new MapSqlParameterSource()
                .addValue("id", question.id())
                .addValue("rideId", question.rideId())
                .addValue("userId", question.userId())
                .addValue("question", question.question())
                .addValue("status", question.status().name())
                .addValue("failureCode", question.failureCode())
                .addValue("answer", question.answerJson())
                .addValue("provider", question.provider())
                .addValue("model", question.model())
                .addValue("promptVersion", question.promptVersion())
                .addValue("latencyMs", question.latencyMs())
                .addValue("createdAt", SqlTime.utc(question.createdAt())));
    }

    public List<TripQuestionRow> list(UUID rideId, UUID userId) {
        return jdbc.query(LIST, new MapSqlParameterSource().addValue("rideId", rideId).addValue("userId", userId),
                (rs, row) -> new TripQuestionRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("question"),
                        TripQuestionStatus.valueOf(rs.getString("status")),
                        rs.getString("failure_code"),
                        rs.getString("answer"),
                        SqlTime.instant(rs.getTimestamp("created_at"))));
    }

    /** @param answerJson the validated answer, or {@code null} unless COMPLETED */
    public record NewQuestion(UUID id, UUID rideId, UUID userId, String question, TripQuestionStatus status,
                              String failureCode, String answerJson, String provider, String model,
                              String promptVersion, Integer latencyMs, Instant createdAt) {
    }

    public record TripQuestionRow(UUID id, String question, TripQuestionStatus status, String failureCode,
                                  String answerJson, Instant createdAt) {
    }
}
