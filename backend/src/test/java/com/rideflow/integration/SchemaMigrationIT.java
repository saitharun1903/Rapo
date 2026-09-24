package com.rideflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.support.IntegrationTestContainers;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The application context starting at all proves Flyway applied V1..Vn and Hibernate's
 * {@code ddl-auto=validate} accepted every entity mapping. The remaining tests pin down DB-level
 * invariants that must hold even if application code has a bug.
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaMigrationIT extends IntegrationTestContainers {

    @Autowired
    private JdbcTemplate jdbc;

    private UUID insertUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, role) VALUES (?, ?, 'x', 'T', ?)",
                id, id + "@example.com", role);
        return id;
    }

    @Test
    void postgisIsInstalled() {
        String version = jdbc.queryForObject("SELECT postgis_lib_version()", String.class);
        assertThat(version).startsWith("3.");
    }

    @Test
    void emailUniquenessIsCaseInsensitive() {
        jdbc.update("INSERT INTO users (email, password_hash, full_name, role) VALUES ('Case@Example.com', 'x', 'A', 'PASSENGER')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, role) VALUES ('case@example.COM', 'x', 'B', 'PASSENGER')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownRoleIsRejectedByCheckConstraint() {
        assertThatThrownBy(() -> insertUser("SUPERUSER")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unverifiedDriverCannotBeOnline() {
        UUID id = insertUser("DRIVER");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO drivers (id, license_number, verification_status, availability) VALUES (?, ?, 'PENDING', 'AVAILABLE')",
                id, "LIC-" + id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void driverCanHaveOnlyOneActiveVehicle() {
        UUID id = insertUser("DRIVER");
        jdbc.update("INSERT INTO drivers (id, license_number) VALUES (?, ?)", id, "LIC-" + id);
        String insertVehicle = """
                INSERT INTO vehicles (driver_id, make, model, color, plate_number, model_year, category, seats)
                VALUES (?, 'Make', 'Model', 'Red', ?, 2022, 'ECONOMY', 4)
                """;
        jdbc.update(insertVehicle, id, "P1" + id.toString().substring(0, 8));

        assertThatThrownBy(() -> jdbc.update(insertVehicle, id, "P2" + id.toString().substring(0, 8)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void driverLocationUsesGeographyWithSpatialIndex() {
        String type = jdbc.queryForObject("""
                SELECT format_type(a.atttypid, a.atttypmod) FROM pg_attribute a
                WHERE a.attrelid = 'driver_locations'::regclass AND a.attname = 'location'
                """, String.class);
        String indexMethod = jdbc.queryForObject("""
                SELECT am.amname FROM pg_class c JOIN pg_am am ON am.oid = c.relam
                WHERE c.relname = 'ix_driver_locations_location'
                """, String.class);

        assertThat(type).isEqualTo("geography(Point,4326)");
        assertThat(indexMethod).isEqualTo("gist");
    }
}
