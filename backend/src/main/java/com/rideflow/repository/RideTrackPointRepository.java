package com.rideflow.repository;

import com.rideflow.geospatial.GeoPoint;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Sampled GPS trail of an in-progress trip; its PostGIS line length is the trip's measured distance. */
@Repository
public class RideTrackPointRepository {

    private static final String POINT = "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography";

    private static final String APPEND = """
            INSERT INTO ride_track_points (ride_id, location, recorded_at) VALUES (:rideId, %s, :recordedAt)
            """.formatted(POINT);

    /** Skips the point when a sample exists that is both recent and close, i.e. keeps it if time OR distance moved enough. */
    private static final String APPEND_IF_SAMPLED = """
            INSERT INTO ride_track_points (ride_id, location, recorded_at)
            SELECT :rideId, %1$s, :recordedAt
            WHERE NOT EXISTS (
                SELECT 1 FROM ride_track_points p
                WHERE p.ride_id = :rideId
                  AND p.recorded_at > :recentSince
                  AND ST_DWithin(p.location, %1$s, :minDistance))
            """.formatted(POINT);

    private static final String SUMMARY = """
            SELECT count(*) AS points,
                   COALESCE(ST_Length(ST_MakeLine(location::geometry ORDER BY recorded_at, id)::geography), 0) AS length_m
            FROM ride_track_points WHERE ride_id = :rideId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public RideTrackPointRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(UUID rideId, GeoPoint point, Instant recordedAt) {
        jdbc.update(APPEND, params(rideId, point, recordedAt));
    }

    /** @return true if the point was stored */
    public boolean appendIfSampled(UUID rideId, GeoPoint point, Instant recordedAt, Duration minInterval,
                                   int minDistanceMeters) {
        return jdbc.update(APPEND_IF_SAMPLED, params(rideId, point, recordedAt)
                .addValue("recentSince", SqlTime.utc(recordedAt.minus(minInterval)))
                .addValue("minDistance", minDistanceMeters)) > 0;
    }

    public TrackSummary summarize(UUID rideId) {
        return jdbc.queryForObject(SUMMARY, new MapSqlParameterSource("rideId", rideId),
                (rs, row) -> new TrackSummary(rs.getInt("points"), rs.getDouble("length_m")));
    }

    private static MapSqlParameterSource params(UUID rideId, GeoPoint point, Instant recordedAt) {
        return new MapSqlParameterSource()
                .addValue("rideId", rideId)
                .addValue("lat", point.lat())
                .addValue("lng", point.lng())
                .addValue("recordedAt", SqlTime.utc(recordedAt));
    }

    public record TrackSummary(int points, double lengthMeters) {
    }
}
