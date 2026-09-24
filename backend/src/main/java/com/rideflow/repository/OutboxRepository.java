package com.rideflow.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** The transactional outbox ({@code outbox_events}). Plain JDBC: rows are written and read as JSON text. */
@Repository
public class OutboxRepository {

    private static final String INSERT = """
            INSERT INTO outbox_events (id, topic, message_key, event_type, payload, created_at)
            VALUES (:id, :topic, :key, :eventType, CAST(:payload AS jsonb), :createdAt)
            """;

    /**
     * SKIP LOCKED lets several instances relay concurrently without taking the same rows, and without
     * waiting for each other.
     */
    private static final String LOCK_UNPUBLISHED = """
            SELECT id, topic, message_key, event_type, payload::text AS payload
            FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY seq
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """;

    private static final String MARK_PUBLISHED = """
            UPDATE outbox_events SET published_at = :now, attempts = attempts + 1, last_error = NULL WHERE id IN (:ids)
            """;

    private static final String RECORD_FAILURE = """
            UPDATE outbox_events SET attempts = attempts + 1, last_error = :error WHERE id = :id
            """;

    private static final String COUNT_UNPUBLISHED = "SELECT count(*) FROM outbox_events WHERE published_at IS NULL";

    private static final String DELETE_PUBLISHED_BEFORE =
            "DELETE FROM outbox_events WHERE published_at IS NOT NULL AND published_at < :before";

    /** Matches {@code outbox_events.last_error}. */
    private static final int MAX_ERROR_LENGTH = 500;

    private final NamedParameterJdbcTemplate jdbc;

    public OutboxRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(OutboxMessage message, Instant createdAt) {
        jdbc.update(INSERT, new MapSqlParameterSource()
                .addValue("id", message.id())
                .addValue("topic", message.topic())
                .addValue("key", message.key())
                .addValue("eventType", message.eventType())
                .addValue("payload", message.payload())
                .addValue("createdAt", SqlTime.utc(createdAt)));
    }

    /** Oldest unpublished rows, locked until the caller's transaction ends. */
    public List<OutboxMessage> lockUnpublished(int limit) {
        return jdbc.query(LOCK_UNPUBLISHED, new MapSqlParameterSource("limit", limit), (rs, row) -> new OutboxMessage(
                rs.getObject("id", UUID.class),
                rs.getString("topic"),
                rs.getString("message_key"),
                rs.getString("event_type"),
                rs.getString("payload")));
    }

    public void markPublished(Collection<UUID> ids, Instant now) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.update(MARK_PUBLISHED, new MapSqlParameterSource().addValue("ids", ids).addValue("now", SqlTime.utc(now)));
    }

    public void recordFailure(UUID id, String error) {
        String truncated = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        jdbc.update(RECORD_FAILURE, new MapSqlParameterSource().addValue("id", id).addValue("error", truncated));
    }

    public long countUnpublished() {
        Long count = jdbc.getJdbcTemplate().queryForObject(COUNT_UNPUBLISHED, Long.class);
        return count == null ? 0 : count;
    }

    public int deletePublishedBefore(Instant before) {
        return jdbc.update(DELETE_PUBLISHED_BEFORE, new MapSqlParameterSource("before", SqlTime.utc(before)));
    }
}
