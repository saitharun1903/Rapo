package com.rideflow.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** The {@code processed_events} idempotency table: one row per (consumer, event) that has been handled. */
@Repository
public class ProcessedEventRepository {

    /**
     * A concurrent duplicate (two consumers after a rebalance) waits on the first insert's row lock and then
     * inserts nothing, so exactly one of them proceeds.
     */
    private static final String INSERT_IF_ABSENT = """
            INSERT INTO processed_events (consumer, event_id, processed_at) VALUES (:consumer, :eventId, :now)
            ON CONFLICT DO NOTHING
            """;

    private static final String DELETE_BEFORE = "DELETE FROM processed_events WHERE processed_at < :before";

    private final NamedParameterJdbcTemplate jdbc;

    public ProcessedEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return {@code true} if the row was inserted, {@code false} if the event was already recorded */
    public boolean insertIfAbsent(String consumer, UUID eventId, Instant now) {
        return jdbc.update(INSERT_IF_ABSENT, new MapSqlParameterSource()
                .addValue("consumer", consumer)
                .addValue("eventId", eventId)
                .addValue("now", SqlTime.utc(now))) == 1;
    }

    public int deleteProcessedBefore(Instant before) {
        return jdbc.update(DELETE_BEFORE, new MapSqlParameterSource("before", SqlTime.utc(before)));
    }
}
