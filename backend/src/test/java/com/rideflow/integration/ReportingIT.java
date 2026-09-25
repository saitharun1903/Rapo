package com.rideflow.integration;

import static com.rideflow.support.GeoTestPoints.HITECH_CITY;
import static com.rideflow.support.RideApi.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.rideflow.entity.PaymentMethod;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.support.IntegrationTestContainers;
import com.rideflow.support.MutableClock;
import com.rideflow.support.RideApi;
import com.rideflow.support.RideFixtures;
import com.rideflow.support.RideFixtures.Actor;
import com.rideflow.support.RideJourneys;
import com.rideflow.support.RideTestConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Driver earnings and the admin console against real PostgreSQL: one ride is completed and paid through the
 * normal flow (including Kafka), and every report must count exactly that ride and that payment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RideTestConfig.class)
class ReportingIT extends IntegrationTestContainers {

    private static final Duration AWAIT = Duration.ofSeconds(20);
    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Duration WINDOW_MARGIN = Duration.ofDays(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RideFixtures fixtures;
    @Autowired
    private MutableClock clock;
    @Autowired
    private JdbcTemplate jdbc;

    private RideApi api;

    @BeforeEach
    void setUp() {
        fixtures.reset();
        api = new RideApi(mvc);
    }

    @Test
    void earningsAndAdminReportsCountTheCompletedRideAndItsPayment() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor driver = RideJourneys.onlineDriver(fixtures, api);
        Actor admin = fixtures.admin();
        Instant start = clock.instant();
        UUID rideId = RideJourneys.completedRide(api, clock, passenger, driver, PaymentMethod.CASH);
        await().atMost(AWAIT).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE ride_id = ?", Long.class, rideId) == 1);
        Map<String, Object> payment = jdbc.queryForMap(
                "SELECT amount, platform_fee, driver_earnings FROM payments WHERE ride_id = ?", rideId);
        String window = "from=" + start.minus(WINDOW_MARGIN) + "&to=" + start.plus(WINDOW_MARGIN);

        // The driver's earnings: their share only, in day buckets that start at local midnight.
        DocumentContext earnings = json(api.call(driver, "GET", "/api/drivers/me/earnings?" + window, null));
        assertThat(money(earnings, "$.total.amount")).isEqualByComparingTo((BigDecimal) payment.get("driver_earnings"));
        assertThat(earnings.read("$.tripCount", Integer.class)).isEqualTo(1);
        assertThat(earnings.read("$.timeZone", String.class)).isEqualTo(REPORTING_ZONE.getId());
        List<String> starts = earnings.read("$.series[*].start");
        assertThat(starts).hasSizeGreaterThanOrEqualTo(2)
                .allSatisfy(bucket -> assertThat(Instant.parse(bucket).atZone(REPORTING_ZONE).toLocalTime())
                        .isEqualTo(LocalTime.MIDNIGHT));
        List<Integer> trips = earnings.read("$.series[*].trips");
        assertThat(trips.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        assertError(api.call(passenger, "GET", "/api/drivers/me/earnings?" + window, null), 403, "FORBIDDEN");

        // The admin overview and time series see the same ride and money.
        DocumentContext overview = json(api.call(admin, "GET", "/api/admin/overview?" + window, null));
        assertThat(overview.read("$.ridesRequested", Integer.class)).isEqualTo(1);
        assertThat(overview.read("$.ridesByStatus.COMPLETED", Integer.class)).isEqualTo(1);
        assertThat(overview.read("$.ridesByStatus.CANCELLED", Integer.class)).isZero();
        assertThat(overview.read("$.completionRate", Double.class)).isEqualTo(1.0);
        assertThat(overview.read("$.cancellationRate", Double.class)).isZero();
        assertThat(overview.read("$.medianSecondsToMatch", Double.class)).isNotNull().isNotNegative();
        assertThat(money(overview, "$.grossFares.amount")).isEqualByComparingTo((BigDecimal) payment.get("amount"));
        assertThat(money(overview, "$.platformFees.amount"))
                .isEqualByComparingTo((BigDecimal) payment.get("platform_fee"));
        assertThat(overview.read("$.verifiedDrivers.AVAILABLE", Integer.class)).isEqualTo(1);

        DocumentContext activity = json(api.call(admin, "GET",
                "/api/admin/analytics/rides?granularity=DAY&" + window, null));
        List<Integer> requested = activity.read("$.series[*].requested");
        List<Integer> completed = activity.read("$.series[*].completed");
        List<String> revenue = activity.read("$.series[*].revenue.amount");
        assertThat(requested.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        assertThat(completed.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        assertThat(revenue.stream().map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo((BigDecimal) payment.get("amount"));

        // Ride search and the support view of one ride.
        DocumentContext rides = json(api.call(admin, "GET", "/api/admin/rides?driverId=" + driver.id(), null));
        assertThat(rides.read("$.totalElements", Integer.class)).isEqualTo(1);
        assertThat(rides.read("$.content[0].id", String.class)).isEqualTo(rideId.toString());
        assertThat(rides.read("$.content[0].passengerId", String.class)).isEqualTo(passenger.id().toString());
        assertThat(rides.read("$.content[0].fareIsFinal", Boolean.class)).isTrue();
        assertThat(json(api.call(admin, "GET", "/api/admin/rides?status=CANCELLED", null))
                .read("$.totalElements", Integer.class)).isZero();

        DocumentContext detail = json(api.call(admin, "GET", "/api/admin/rides/" + rideId, null));
        assertThat(detail.read("$.ride.status", String.class)).isEqualTo("COMPLETED");
        assertThat(detail.read("$.passenger.id", String.class)).isEqualTo(passenger.id().toString());
        List<String> timeline = detail.read("$.timeline[*].to");
        assertThat(timeline).startsWith("REQUESTED").endsWith("COMPLETED");
        List<Map<String, Object>> offers = detail.read("$.offers");
        assertThat(offers).singleElement().satisfies(offer -> {
            assertThat(offer.get("driverId")).isEqualTo(driver.id().toString());
            assertThat(offer.get("status")).isEqualTo("ACCEPTED");
        });
        assertError(api.call(admin, "GET", "/api/admin/rides/" + UUID.randomUUID(), null), 404, "RIDE_NOT_FOUND");

        // Only admins, and only valid windows.
        assertError(api.call(passenger, "GET", "/api/admin/overview?" + window, null), 403, "FORBIDDEN");
        assertError(api.call(admin, "GET", "/api/admin/overview?from=" + start + "&to=" + start, null), 400,
                "INVALID_DATE_RANGE");
        assertError(api.call(admin, "GET", "/api/admin/analytics/rides?granularity=HOUR&from=" + start + "&to="
                + start.plus(Duration.ofDays(30)), null), 400, "INVALID_DATE_RANGE");
    }

    @Test
    void systemStatusReportsHealthAndLiveMetrics() throws Exception {
        DocumentContext system = json(api.call(fixtures.admin(), "GET", "/api/admin/system", null));

        assertThat(system.read("$.health", String.class)).isNotBlank();
        Map<String, String> components = system.read("$.components");
        assertThat(components).containsKey("db");
        assertThat(system.read("$.outboxPending", Integer.class)).isNotNull().isNotNegative();
        assertThat(system.read("$.webSocketSessions", Integer.class)).isNotNull();
        assertThat(system.read("$.aiCircuitOpen", Boolean.class)).isFalse();
        // No SENTRY_DSN here, so there is nowhere to send a test error.
        assertThat(system.read("$.errorReporting", Boolean.class)).isFalse();
        assertError(api.call(fixtures.admin(), "POST", "/api/admin/system/test-error", null), 409,
                "ERROR_REPORTING_DISABLED");
    }

    @Test
    void vehiclesAreReplacedOnlyOfflineAndTheChangeIsAudited() throws Exception {
        Actor driver = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        Actor admin = fixtures.admin();
        String existingPlate = JsonPath.read(body(api.call(driver, "GET", "/api/drivers/me", null)),
                "$.vehicle.plateNumber");

        DocumentContext replaced = json(api.call(driver, "PUT", "/api/drivers/me/vehicle", vehicle("TS09XL4321", "XL")));
        assertThat(replaced.read("$.vehicle.plateNumber", String.class)).isEqualTo("TS09XL4321");
        assertThat(replaced.read("$.vehicle.category", String.class)).isEqualTo("XL");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vehicles WHERE driver_id = ? AND active", Long.class,
                driver.id())).isEqualTo(1);

        // A plate ever registered cannot be registered again, including the driver's own retired one.
        assertError(api.call(driver, "PUT", "/api/drivers/me/vehicle", vehicle(existingPlate, "ECONOMY")), 409,
                "PLATE_TAKEN");

        assertStatus(api.goOnline(driver, HITECH_CITY), 200);
        assertError(api.call(driver, "PUT", "/api/drivers/me/vehicle", vehicle("TS09AB0001", "ECONOMY")), 409,
                "INVALID_DRIVER_STATE");

        DocumentContext audit = json(api.call(admin, "GET",
                "/api/admin/audit-logs?action=DRIVER_VEHICLE_REPLACED&entityType=DRIVER", null));
        List<String> entities = audit.read("$.content[*].entityId");
        assertThat(entities).containsOnlyOnce(driver.id().toString());
        assertError(api.call(driver, "GET", "/api/admin/audit-logs", null), 403, "FORBIDDEN");
    }

    private static String vehicle(String plate, String category) {
        return """
                {"make":"Toyota","model":"Innova","color":"Silver","plateNumber":"%s","modelYear":2023,
                 "category":"%s","seats":6}
                """.formatted(plate, category);
    }

    private static BigDecimal money(DocumentContext document, String path) {
        return new BigDecimal(document.read(path, String.class));
    }

    private static DocumentContext json(MvcResult result) throws Exception {
        assertStatus(result, 200);
        return JsonPath.parse(body(result));
    }

    private static void assertStatus(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(status);
    }

    private static void assertError(MvcResult result, int status, String code) throws Exception {
        assertStatus(result, status);
        assertThat(JsonPath.<String>read(body(result), "$.code")).isEqualTo(code);
    }
}
