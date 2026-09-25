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
import com.rideflow.security.SignedClientIp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Rate limits over HTTP against a real Redis, with small limits so the tests stay fast. A real server as well as
 * MockMvc: which forwarding headers decide the client address is up to Tomcat, which MockMvc bypasses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "rideflow.rate-limit.enabled=true",
    "rideflow.rate-limit.rules.LOGIN.limit=" + RateLimitIT.LOGIN_LIMIT,
    "rideflow.rate-limit.rules.REGISTER.limit=" + RateLimitIT.REGISTER_LIMIT,
    "rideflow.rate-limit.rules.ROUTE.limit=" + RateLimitIT.ROUTE_LIMIT,
    "rideflow.security.client-ip.signing-secret=" + RateLimitIT.CLIENT_IP_SECRET,
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
    static final int LOGIN_LIMIT = 2;
    static final int REGISTER_LIMIT = 2;
    static final int ROUTE_LIMIT = 2;
    private static final String ROUTE_IN_THE_CITY = "/api/geo/route?fromLat=17.44&fromLng=78.38&toLat=17.42&toLng=78.47";
    /** From Hyderabad to Mumbai: far outside the service area. */
    private static final String ROUTE_TO_MUMBAI = "/api/geo/route?fromLat=17.44&fromLng=78.38&toLat=19.076&toLng=72.8777";
    /** Requests made after a limit is reached, each claiming yet another address. */
    private static final int ATTEMPTS_OVER_LIMIT = 5;
    /** Documentation range (RFC 5737); each request claims a different address in it. */
    private static final String FORGED_IP_PREFIX = "203.0.113.";
    /** The default CORS_ALLOWED_ORIGINS, and the frontend's host as its /api rewrite reports it. */
    private static final String FRONTEND_ORIGIN = "http://localhost:3000";
    private static final String FRONTEND_HOST = "localhost:3000";
    /** What the frontend's proxy.ts shares with the backend (CLIENT_IP_SIGNING_SECRET). */
    static final String CLIENT_IP_SECRET = "rate-limit-it-client-ip-signing-secret-0123456789";
    private static final String BROWSER_IP = "198.51.100.21";
    private static final String OTHER_BROWSER_IP = "198.51.100.22";

    @LocalServerPort
    private int port;
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
    @Autowired
    private Clock clock;

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

    private static String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private static String registerBody() {
        return """
                {"email":"reg-%s@example.com","password":"%s","fullName":"New User","accountType":"PASSENGER"}
                """.formatted(UUID.randomUUID(), PASSWORD);
    }

    private MvcResult login(String email, String password, String fromIp) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(fromIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andReturn();
    }

    private MvcResult register(String fromIp) throws Exception {
        return mvc.perform(post("/api/auth/register")
                        .with(request -> {
                            request.setRemoteAddr(fromIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody()))
                .andReturn();
    }

    /** A real request to the running server, from this machine, with the given extra headers. */
    private HttpResponse<String> postOverHttp(String path, String json, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(request::header);
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static Map<String, String> claimingToBe(int client) {
        return Map.of("X-Forwarded-For", FORGED_IP_PREFIX + client);
    }

    /** The headers the frontend's proxy.ts adds, signed independently of the code under test. */
    private Map<String, String> vouchedForBy(String secret, String ip) throws Exception {
        long seconds = clock.instant().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(("v1\n" + seconds + "\n" + ip).getBytes(StandardCharsets.UTF_8));
        return Map.of(SignedClientIp.IP_HEADER, ip, SignedClientIp.SIGNATURE_HEADER,
                "v1." + seconds + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature));
    }

    private static void assertRateLimited(MvcResult result, long maxRetryAfterSeconds) throws Exception {
        assertRateLimited(result.getResponse().getStatus(), body(result),
                result.getResponse().getHeader(HttpHeaders.RETRY_AFTER), maxRetryAfterSeconds);
    }

    private static void assertRateLimited(HttpResponse<String> response, long maxRetryAfterSeconds) {
        assertRateLimited(response.statusCode(), response.body(),
                response.headers().firstValue(HttpHeaders.RETRY_AFTER).orElse(null), maxRetryAfterSeconds);
    }

    private static void assertRateLimited(int status, String body, String retryAfter, long maxRetryAfterSeconds) {
        assertThat(status).as(body).isEqualTo(429);
        assertThat(JsonPath.<String>read(body, "$.code")).isEqualTo("RATE_LIMITED");
        assertThat(retryAfter).as(HttpHeaders.RETRY_AFTER).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, maxRetryAfterSeconds);
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

    /**
     * Anyone can send X-Forwarded-For: straight to the backend's port, or through the frontend's /api rewrite,
     * which passes the header on as the browser sent it. Neither is a trusted proxy (TRUSTED_PROXIES is empty),
     * so claiming a new address on every attempt must not open a new bucket.
     */
    @Test
    void forgingXForwardedForDoesNotBuyMoreLoginAttempts() throws Exception {
        String email = existingUser();
        int claimed = 0;
        for (int attempt = 0; attempt < LOGIN_LIMIT; attempt++) {
            HttpResponse<String> wrong = postOverHttp("/api/auth/login",
                    loginBody(email, "Wrong-password-" + attempt), claimingToBe(++claimed));
            assertThat(wrong.statusCode()).as(wrong.body()).isEqualTo(401);
        }
        for (int attempt = 0; attempt < ATTEMPTS_OVER_LIMIT; attempt++) {
            assertRateLimited(postOverHttp("/api/auth/login", loginBody(email, PASSWORD), claimingToBe(++claimed)),
                    LOGIN_WINDOW_SECONDS);
        }
    }

    @Test
    void forgingXForwardedForDoesNotBuyMoreRegistrations() throws Exception {
        int claimed = 0;
        for (int attempt = 0; attempt < REGISTER_LIMIT; attempt++) {
            HttpResponse<String> created = postOverHttp("/api/auth/register", registerBody(), claimingToBe(++claimed));
            assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        }
        for (int attempt = 0; attempt < ATTEMPTS_OVER_LIMIT; attempt++) {
            assertRateLimited(postOverHttp("/api/auth/register", registerBody(), claimingToBe(++claimed)),
                    LOGIN_WINDOW_SECONDS);
        }
    }

    /**
     * What the frontend's /api rewrite sends: the browser's Origin, the Host rewritten to the backend, and
     * X-Forwarded-Host naming the frontend. That hop is not trusted, so the Origin is checked against
     * CORS_ALLOWED_ORIGINS instead of passing as same-origin on headers the caller chose.
     */
    @Test
    void aForwardedLoginIsCheckedAgainstTheAllowedOriginsNotItsForwardingHeaders() throws Exception {
        HttpResponse<String> fromFrontend = postOverHttp("/api/auth/login", loginBody(existingUser(), PASSWORD),
                Map.of(HttpHeaders.ORIGIN, FRONTEND_ORIGIN, "X-Forwarded-Host", FRONTEND_HOST,
                        "X-Forwarded-Proto", "http"));
        assertThat(fromFrontend.statusCode()).as(fromFrontend.body()).isEqualTo(200);
        assertThat(fromFrontend.headers().firstValue(HttpHeaders.SET_COOKIE)).isPresent();

        HttpResponse<String> fromForeignSite = postOverHttp("/api/auth/login", loginBody(existingUser(), PASSWORD),
                Map.of(HttpHeaders.ORIGIN, "https://evil.example", "X-Forwarded-Host", "evil.example",
                        "X-Forwarded-Proto", "https", "X-Forwarded-Port", "443"));
        assertThat(fromForeignSite.statusCode()).as(fromForeignSite.body()).isEqualTo(403);
    }

    /**
     * Two browsers behind the same frontend: the backend sees one peer, but the frontend signs each browser's
     * address, so one browser using up an account's attempts does not lock the other out.
     */
    @Test
    void addressesSignedByTheFrontendGetTheirOwnBuckets() throws Exception {
        String email = existingUser();
        for (int attempt = 0; attempt < LOGIN_LIMIT; attempt++) {
            HttpResponse<String> wrong = postOverHttp("/api/auth/login",
                    loginBody(email, "Wrong-password-" + attempt), vouchedForBy(CLIENT_IP_SECRET, BROWSER_IP));
            assertThat(wrong.statusCode()).as(wrong.body()).isEqualTo(401);
        }
        assertRateLimited(postOverHttp("/api/auth/login", loginBody(email, PASSWORD),
                vouchedForBy(CLIENT_IP_SECRET, BROWSER_IP)), LOGIN_WINDOW_SECONDS);

        HttpResponse<String> otherBrowser = postOverHttp("/api/auth/login", loginBody(email, PASSWORD),
                vouchedForBy(CLIENT_IP_SECRET, OTHER_BROWSER_IP));
        assertThat(otherBrowser.statusCode()).as(otherBrowser.body()).isEqualTo(200);
    }

    /** Without the secret, a caller's claims all land in the bucket of the address it connects from. */
    @Test
    void addressesSignedWithoutTheSecretAreIgnored() throws Exception {
        String email = existingUser();
        String wrongSecret = "not-the-frontends-secret-but-just-as-long-0123456789";
        int claimed = 0;
        for (int attempt = 0; attempt < LOGIN_LIMIT; attempt++) {
            HttpResponse<String> wrong = postOverHttp("/api/auth/login", loginBody(email, "Wrong-password-" + attempt),
                    vouchedForBy(wrongSecret, FORGED_IP_PREFIX + ++claimed));
            assertThat(wrong.statusCode()).as(wrong.body()).isEqualTo(401);
        }
        assertRateLimited(postOverHttp("/api/auth/login", loginBody(email, PASSWORD),
                vouchedForBy(wrongSecret, FORGED_IP_PREFIX + ++claimed)), LOGIN_WINDOW_SECONDS);
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

    @Test
    void routePreviewsAreLimitedPerUserAndOnlyServedNearTheServiceArea() throws Exception {
        Actor user = fixtures.passenger();

        MvcResult outside = mvc.perform(get(ROUTE_TO_MUMBAI).header(HttpHeaders.AUTHORIZATION, user.bearer())).andReturn();
        assertThat(outside.getResponse().getStatus()).as(body(outside)).isEqualTo(422);
        assertThat(JsonPath.<String>read(body(outside), "$.code")).isEqualTo("OUTSIDE_SERVICE_AREA");

        // A refused route is not counted, so the whole limit is still there.
        for (int i = 0; i < ROUTE_LIMIT; i++) {
            MvcResult allowed = mvc.perform(get(ROUTE_IN_THE_CITY).header(HttpHeaders.AUTHORIZATION, user.bearer())).andReturn();
            assertThat(allowed.getResponse().getStatus()).as(body(allowed)).isEqualTo(200);
        }
        assertRateLimited(mvc.perform(get(ROUTE_IN_THE_CITY).header(HttpHeaders.AUTHORIZATION, user.bearer())).andReturn(),
                LOGIN_WINDOW_SECONDS);
        MvcResult otherUser = mvc.perform(get(ROUTE_IN_THE_CITY)
                .header(HttpHeaders.AUTHORIZATION, fixtures.passenger().bearer())).andReturn();
        assertThat(otherUser.getResponse().getStatus()).isEqualTo(200);
    }
}
