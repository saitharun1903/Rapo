package com.rideflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rideflow.entity.AuditAction;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.repository.AuditLogRepository;
import com.rideflow.repository.UserRepository;
import com.rideflow.support.IntegrationTestContainers;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Full HTTP → security → service → PostgreSQL flows with no mocks. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndOnboardingFlowIT extends IntegrationTestContainers {

    private static final String PASSWORD = "Integration-pass-42";
    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "rideflow";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private AuditLogRepository auditLogs;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private record Session(String accessToken, String refreshToken, String userId) {
    }

    private static String uniqueEmail() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    private void register(String email, String accountType) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s","fullName":"IT User","accountType":"%s"}
                        """.formatted(email, PASSWORD, accountType)))
                .andExpect(status().isCreated());
    }

    private Session login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return session(result);
    }

    private static Session session(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return new Session(
                JsonPath.read(body, "$.accessToken"),
                result.getResponse().getCookie("rf_refresh").getValue(),
                JsonPath.read(body, "$.user.id"));
    }

    private MvcResult refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie("rf_refresh", refreshToken)))
                .andReturn();
    }

    private static String bearer(Session session) {
        return "Bearer " + session.accessToken();
    }

    @Test
    void registerLoginAccessProfileAndRotateRefreshToken() throws Exception {
        String email = uniqueEmail();
        register(email.toUpperCase(), "PASSENGER");

        Session session = login(email);
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("PASSENGER"));

        MvcResult rotated = refresh(session.refreshToken());
        assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
        Session lost = session(rotated);
        assertThat(lost.refreshToken()).isNotEqualTo(session.refreshToken());

        // The response carrying that token never reached the browser (a reload mid-refresh), so it sends the old
        // one again at once: within the grace, with the successor unused, the session goes on.
        MvcResult retried = refresh(session.refreshToken());
        assertThat(retried.getResponse().getStatus()).isEqualTo(200);
        Session next = session(retried);
        assertThat(next.refreshToken()).isNotIn(session.refreshToken(), lost.refreshToken());
        MvcResult used = refresh(next.refreshToken());
        assertThat(used.getResponse().getStatus()).isEqualTo(200);
        Session latest = session(used);

        // Once the client has used its new token, the old one coming back is theft: the whole family, including
        // the newest token, is revoked.
        MvcResult reuse = refresh(session.refreshToken());
        assertThat(reuse.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(reuse.getResponse().getContentAsString(), "$.code")).isEqualTo("SESSION_REVOKED");
        assertThat(refresh(latest.refreshToken()).getResponse().getStatus()).isEqualTo(401);
        assertThat(auditLogs.findByEntityIdAndAction(UUID.fromString(session.userId()),
                AuditAction.REFRESH_TOKEN_REUSE_DETECTED)).hasSize(1);
    }

    @Test
    void duplicateEmailIsConflictRegardlessOfCase() throws Exception {
        String email = uniqueEmail();
        register(email, "PASSENGER");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s","fullName":"Dup","accountType":"PASSENGER"}
                        """.formatted(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void failedLoginIsAuditedEvenThoughRequestFails() throws Exception {
        String email = uniqueEmail();
        register(email, "PASSENGER");
        UUID userId = users.findByEmail(email).orElseThrow().getId();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Wrong-pass-123\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertThat(auditLogs.findByEntityIdAndAction(userId, AuditAction.LOGIN_FAILED)).hasSize(1);
    }

    @Test
    void driverOnboardingThroughAdminVerification() throws Exception {
        String driverEmail = uniqueEmail();
        register(driverEmail, "DRIVER");
        Session driver = login(driverEmail);
        String plate = "IT" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        mvc.perform(get("/api/drivers/me").header(HttpHeaders.AUTHORIZATION, bearer(driver)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_PROFILE_NOT_FOUND"));

        mvc.perform(post("/api/drivers/me/profile").header(HttpHeaders.AUTHORIZATION, bearer(driver))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"licenseNumber":"LIC-%s","vehicle":{"make":"Honda","model":"City","color":"Grey",
                                 "plateNumber":"%s","modelYear":2023,"category":"COMFORT","seats":4}}
                                """.formatted(plate, plate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.availability").value("OFFLINE"))
                .andExpect(jsonPath("$.vehicle.plateNumber").value(plate));

        Session admin = loginAsNewAdmin();
        mvc.perform(get("/api/admin/drivers").param("verificationStatus", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')].vehicle.plateNumber".formatted(driver.userId()))
                        .value(plate));

        mvc.perform(post("/api/admin/drivers/{id}/verify", driver.userId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        mvc.perform(post("/api/admin/drivers/{id}/verify", driver.userId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DRIVER_STATE"));
    }

    @Test
    void suspendingUserRevokesTheirSessions() throws Exception {
        String email = uniqueEmail();
        register(email, "PASSENGER");
        Session passenger = login(email);
        Session admin = loginAsNewAdmin();

        mvc.perform(patch("/api/admin/users/{id}/status", passenger.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"Fraud investigation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        MvcResult refreshAfterSuspension = refresh(passenger.refreshToken());
        assertThat(refreshAfterSuspension.getResponse().getStatus()).isEqualTo(401);
        assertThat(auditLogs.findByEntityIdAndAction(UUID.fromString(passenger.userId()),
                AuditAction.REFRESH_TOKEN_REUSE_DETECTED)).isEmpty();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    /** Admins cannot self-register; tests create one directly, as the bootstrapper would. */
    private Session loginAsNewAdmin() throws Exception {
        String email = uniqueEmail();
        users.save(User.register(email, null, passwordEncoder.encode(PASSWORD), "IT Admin", Role.ADMIN));
        return login(email);
    }
}
