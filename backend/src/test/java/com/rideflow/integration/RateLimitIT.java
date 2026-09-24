package com.rideflow.integration;

import static com.rideflow.support.RideApi.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.repository.UserRepository;
import com.rideflow.support.IntegrationTestContainers;
import com.rideflow.support.RideFixtures;
import com.rideflow.support.RideFixtures.Actor;
import com.rideflow.support.RideTestConfig;
import com.rideflow.support.StubProvidersConfig;
import com.rideflow.support.StubProvidersConfig.CountingGeocoder;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Rate limits over HTTP against a real Redis, with small limits so the tests stay fast. */
@SpringBootTest(properties = {
    "rideflow.rate-limit.enabled=true",
    "rideflow.rate-limit.rules.LOGIN.limit=2",
    "rideflow.rate-limit.rules.REGISTER.limit=2",
    // A long window keeps the upstream test independent of how fast the CI runner is.
    "rideflow.rate-limit.rules.GEOCODING_UPSTREAM.window=1m"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({RideTestConfig.class, StubProvidersConfig.class})
class RateLimitIT extends IntegrationTestContainers {

    private static final String PASSWORD = "Correct-horse-9";
    private static final String ATTACKER_IP = "198.51.100.7";
    private static final String OTHER_IP = "198.51.100.8";
    private static final long LOGIN_WINDOW_SECONDS = 60;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RideFixtures fixtures;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private CountingGeocoder geocoder;

    @BeforeEach
    void setUp() {
        fixtures.reset();
        geocoder.reset();
    }

    private String existingUser() {
        String email = "rl-" + UUID.randomUUID() + "@example.com";
        users.save(User.register(email, null, passwordEncoder.encode(PASSWORD), "Rate Limited", Role.PASSENGER));
        return email;
    }

    private MvcResult login(String email, String password, String fromIp) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(fromIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andReturn();
    }

    private MvcResult register(String fromIp) throws Exception {
        return mvc.perform(post("/api/auth/register")
                        .with(request -> {
                            request.setRemoteAddr(fromIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reg-%s@example.com","password":"%s","fullName":"New User",
                                 "accountType":"PASSENGER"}
                                """.formatted(UUID.randomUUID(), PASSWORD)))
                .andReturn();
    }

    private static void assertRateLimited(MvcResult result, long maxRetryAfterSeconds) throws Exception {
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(429);
        assertThat(JsonPath.<String>read(body(result), "$.code")).isEqualTo("RATE_LIMITED");
        assertThat(Long.parseLong(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
                .isBetween(1L, maxRetryAfterSeconds);
    }

    @Test
    void loginAttemptsAreLimitedPerIpAndEmailEvenWithTheRightPassword() throws Exception {
        String email = existingUser();

        assertThat(login(email, "Wrong-password-1", ATTACKER_IP).getResponse().getStatus()).isEqualTo(401);
        assertThat(login(email, "Wrong-password-2", ATTACKER_IP).getResponse().getStatus()).isEqualTo(401);
        assertRateLimited(login(email, PASSWORD, ATTACKER_IP), LOGIN_WINDOW_SECONDS);

        // The account owner on another network is unaffected, and so is another account from the same IP.
        assertThat(login(email, PASSWORD, OTHER_IP).getResponse().getStatus()).isEqualTo(200);
        assertThat(login(existingUser(), PASSWORD, ATTACKER_IP).getResponse().getStatus()).isEqualTo(200);

        // The counters in Redis carry no personal data.
        assertThat(redis.keys("rl:login:*")).isNotEmpty()
                .allSatisfy(key -> assertThat(key).doesNotContain(email).doesNotContain(ATTACKER_IP));
    }

    @Test
    void registrationIsLimitedPerIp() throws Exception {
        assertThat(register(ATTACKER_IP).getResponse().getStatus()).isEqualTo(201);
        assertThat(register(ATTACKER_IP).getResponse().getStatus()).isEqualTo(201);
        assertRateLimited(register(ATTACKER_IP), LOGIN_WINDOW_SECONDS);
        assertThat(register(OTHER_IP).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void theGeocoderIsCalledAtMostOncePerUpstreamWindowAcrossAllUsers() throws Exception {
        Actor first = fixtures.passenger();
        Actor second = fixtures.passenger();

        MvcResult allowed = mvc.perform(get("/api/geo/search?q=Charminar")
                .header(HttpHeaders.AUTHORIZATION, first.bearer())).andReturn();
        MvcResult busy = mvc.perform(get("/api/geo/search?q=Golconda")
                .header(HttpHeaders.AUTHORIZATION, second.bearer())).andReturn();
        MvcResult cached = mvc.perform(get("/api/geo/search?q=charminar")
                .header(HttpHeaders.AUTHORIZATION, second.bearer())).andReturn();

        assertThat(allowed.getResponse().getStatus()).isEqualTo(200);
        assertThat(busy.getResponse().getStatus()).isEqualTo(503);
        assertThat(JsonPath.<String>read(body(busy), "$.code")).isEqualTo("GEOCODING_UNAVAILABLE");
        assertThat(busy.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        // Cache hits never spend the upstream budget.
        assertThat(cached.getResponse().getStatus()).isEqualTo(200);
        assertThat(geocoder.calls()).isEqualTo(1);
    }
}
