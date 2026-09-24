package com.rideflow.repository;

import com.rideflow.entity.VehicleCategory;
import com.rideflow.geospatial.GeoPoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostGIS access to drivers' current positions (one row per driver). Plain JDBC because the queries are
 * spatial SQL, not entity graphs. The proximity query is documented in docs/architecture.md section 7.2.
 */
@Repository
public class DriverLocationRepository {

    /** Longitude first: ST_MakePoint(x, y). */
    private static final String POINT = "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography";

    private static final String UPSERT = """
            INSERT INTO driver_locations (driver_id, location, heading_deg, speed_mps, accuracy_m, recorded_at, updated_at)
            VALUES (:driverId, %s, :heading, :speed, :accuracy, :recordedAt, :now)
            ON CONFLICT (driver_id) DO UPDATE
            SET location = EXCLUDED.location, heading_deg = EXCLUDED.heading_deg, speed_mps = EXCLUDED.speed_mps,
                accuracy_m = EXCLUDED.accuracy_m, recorded_at = EXCLUDED.recorded_at, updated_at = EXCLUDED.updated_at
            WHERE driver_locations.recorded_at <= EXCLUDED.recorded_at
            """.formatted(POINT);

    private static final String FIND = """
            SELECT ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lng, heading_deg, recorded_at, updated_at
            FROM driver_locations WHERE driver_id = :driverId
            """;

    private static final String NEARBY_SELECT = """
            SELECT d.id AS driver_id, v.id AS vehicle_id, v.category,
                   ST_Y(dl.location::geometry) AS lat, ST_X(dl.location::geometry) AS lng,
                   ST_Distance(dl.location, %1$s) AS distance_m
            FROM driver_locations dl
            JOIN drivers d  ON d.id = dl.driver_id
            JOIN vehicles v ON v.driver_id = d.id AND v.active
            WHERE d.availability = 'AVAILABLE'
              AND d.verification_status = 'VERIFIED'
              AND dl.updated_at > :freshSince
              AND ST_DWithin(dl.location, %1$s, :radius)
            """.formatted(POINT);
    private static final String NEARBY_CATEGORY_FILTER = " AND v.category = :category";
    private static final String NEARBY_EXCLUDE_BUSY = """
             AND NOT EXISTS (SELECT 1 FROM ride_offers o WHERE o.driver_id = d.id AND o.status = 'PENDING')
             AND NOT EXISTS (SELECT 1 FROM ride_offers o WHERE o.driver_id = d.id AND o.ride_id = :rideId)
            """;
    private static final String NEARBY_ORDER = " ORDER BY dl.location <-> " + POINT + " LIMIT :limit";

    private static final String COUNT_AVAILABLE = """
            SELECT count(*) FROM driver_locations dl JOIN drivers d ON d.id = dl.driver_id
            WHERE d.availability = 'AVAILABLE' AND d.verification_status = 'VERIFIED'
              AND dl.updated_at > :freshSince AND ST_DWithin(dl.location, %s, :radius)
            """.formatted(POINT);

    private static final String SILENT_AVAILABLE = """
            SELECT d.id FROM drivers d
            LEFT JOIN driver_locations dl ON dl.driver_id = d.id
            WHERE d.availability = 'AVAILABLE' AND (dl.updated_at IS NULL OR dl.updated_at < :silentSince)
            ORDER BY dl.updated_at NULLS FIRST
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DriverLocationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Stores the position unless a newer one is already stored (out-of-order reports never regress it). Used
     * when a driver goes online, so matching can find them at once.
     *
     * @return {@code true} if this report became the driver's current position
     */
    public boolean upsert(UUID driverId, GeoPoint point, Integer headingDeg, Double speedMps, Double accuracyMeters,
                          Instant recordedAt, Instant now) {
        return jdbc.update(UPSERT, point(point)
                .addValue("driverId", driverId)
                .addValue("heading", headingDeg)
                .addValue("speed", speedMps)
                .addValue("accuracy", accuracyMeters)
                .addValue("recordedAt", SqlTime.utc(recordedAt))
                .addValue("now", SqlTime.utc(now))) == 1;
    }

    /**
     * Writes a batch of positions from the location consumer, one statement per driver in a single JDBC
     * batch. The same never-regress rule applies, so replaying a batch is harmless.
     */
    public void upsertAll(List<LocationWrite> positions) {
        if (positions.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = positions.stream().map(position -> point(position.point())
                        .addValue("driverId", position.driverId())
                        .addValue("heading", position.headingDeg())
                        .addValue("speed", position.speedMps())
                        .addValue("accuracy", position.accuracyMeters())
                        .addValue("recordedAt", SqlTime.utc(position.recordedAt()))
                        .addValue("now", SqlTime.utc(position.receivedAt())))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT, batch);
    }

    /** One position report to persist; {@code receivedAt} (server time) becomes {@code updated_at}. */
    public record LocationWrite(UUID driverId, GeoPoint point, Integer headingDeg, Double speedMps,
                                Double accuracyMeters, Instant recordedAt, Instant receivedAt) {
    }

    public Optional<DriverPosition> find(UUID driverId) {
        return jdbc.query(FIND, new MapSqlParameterSource("driverId", driverId), (rs, row) -> new DriverPosition(
                        new GeoPoint(rs.getDouble("lat"), rs.getDouble("lng")),
                        (Integer) rs.getObject("heading_deg"),
                        SqlTime.instant(rs.getTimestamp("recorded_at")),
                        SqlTime.instant(rs.getTimestamp("updated_at"))))
                .stream().findFirst();
    }

    /**
     * Nearest available, verified drivers with a fresh position within {@code radiusMeters}, using the GiST
     * index for both the radius filter (ST_DWithin) and the ordering (KNN {@code <->}).
     *
     * @param category        restrict to a vehicle category, or {@code null} for any
     * @param excludeForRide  when not null, skip drivers holding any pending offer or already offered this ride
     */
    public List<NearbyDriver> findAvailableNear(GeoPoint point, double radiusMeters, VehicleCategory category,
                                                Instant freshSince, UUID excludeForRide, int limit) {
        StringBuilder sql = new StringBuilder(NEARBY_SELECT);
        MapSqlParameterSource params = point(point)
                .addValue("radius", radiusMeters)
                .addValue("freshSince", SqlTime.utc(freshSince))
                .addValue("limit", limit);
        if (category != null) {
            sql.append(NEARBY_CATEGORY_FILTER);
            params.addValue("category", category.name());
        }
        if (excludeForRide != null) {
            sql.append(NEARBY_EXCLUDE_BUSY);
            params.addValue("rideId", excludeForRide);
        }
        sql.append(NEARBY_ORDER);
        return jdbc.query(sql.toString(), params, (rs, row) -> new NearbyDriver(
                rs.getObject("driver_id", UUID.class),
                rs.getObject("vehicle_id", UUID.class),
                VehicleCategory.valueOf(rs.getString("category")),
                new GeoPoint(rs.getDouble("lat"), rs.getDouble("lng")),
                rs.getDouble("distance_m")));
    }

    public long countAvailableNear(GeoPoint point, double radiusMeters, Instant freshSince) {
        Long count = jdbc.queryForObject(COUNT_AVAILABLE, point(point)
                .addValue("radius", radiusMeters)
                .addValue("freshSince", SqlTime.utc(freshSince)), Long.class);
        return count == null ? 0 : count;
    }

    /** AVAILABLE drivers with no location update since {@code silentSince}, longest-silent first. */
    public List<UUID> findSilentAvailableDrivers(Instant silentSince, int limit) {
        return jdbc.queryForList(SILENT_AVAILABLE, new MapSqlParameterSource()
                .addValue("silentSince", SqlTime.utc(silentSince))
                .addValue("limit", limit), UUID.class);
    }

    private static MapSqlParameterSource point(GeoPoint point) {
        return new MapSqlParameterSource().addValue("lat", point.lat()).addValue("lng", point.lng());
    }
}
