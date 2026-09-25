package com.rideflow.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.rideflow.support.RideApi.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.jayway.jsonpath.JsonPath;
import com.rideflow.support.IntegrationTestContainers;
import com.rideflow.support.RideApi;
import com.rideflow.support.RideFixtures;
import com.rideflow.support.RideFixtures.Actor;
import com.rideflow.support.RideTestConfig;
import io.sentry.Sentry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Error reporting against a WireMock server playing Sentry's ingest API: the admin console's test error must
 * arrive as an event with the id the API returned, and must not carry the caller's credentials. Sentry is
 * process-global, so the context is discarded and Sentry closed afterwards; other tests run with it off.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RideTestConfig.class)
@DirtiesContext
class ErrorReportingIT extends IntegrationTestContainers {

    private static final WireMockServer SENTRY = new WireMockServer(options().dynamicPort());
    private static final String PROJECT_ID = "42";
    private static final String ENVELOPES = "/api/" + PROJECT_ID + "/envelope/";
    private static final Duration AWAIT = Duration.ofSeconds(20);
    private static final int GZIP_MAGIC_FIRST_BYTE = 0x1f;

    static {
        SENTRY.start();
    }

    @DynamicPropertySource
    static void sentryDsn(DynamicPropertyRegistry registry) {
        registry.add("sentry.dsn", () -> "http://public-key@localhost:" + SENTRY.port() + "/" + PROJECT_ID);
    }

    @AfterAll
    static void closeSentry() {
        Sentry.close();
        SENTRY.stop();
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RideFixtures fixtures;

    private RideApi api;

    @BeforeEach
    void setUp() {
        fixtures.reset();
        api = new RideApi(mvc);
        SENTRY.resetAll();
        SENTRY.stubFor(post(urlPathMatching(ENVELOPES)).willReturn(aResponse().withStatus(200)));
    }

    @Test
    void theAdminTestErrorReachesSentryWithoutTheCallersCredentials() throws Exception {
        Actor admin = fixtures.admin();
        assertThat(JsonPath.<Boolean>read(body(api.call(admin, "GET", "/api/admin/system", null)),
                "$.errorReporting")).isTrue();

        MvcResult result = api.call(admin, "POST", "/api/admin/system/test-error", null);
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(202);
        String eventId = JsonPath.read(body(result), "$.eventId");

        String envelope = await().atMost(AWAIT).until(() -> envelopeContaining(eventId), Optional::isPresent)
                .orElseThrow();
        assertThat(envelope)
                .contains("Deliberate test error from the RideFlow admin console")
                // The request is attached (so the header check below means something), minus its credentials.
                .contains("/api/admin/system/test-error")
                .doesNotContainPattern("(?i)\"authorization\"\\s*:")
                .doesNotContain(admin.bearer().substring("Bearer ".length()));
    }

    private static Optional<String> envelopeContaining(String eventId) {
        return SENTRY.findAll(postRequestedFor(urlPathMatching(ENVELOPES)))
                .stream()
                .map(ErrorReportingIT::text)
                .filter(text -> text.contains(eventId))
                .findFirst();
    }

    /** The SDK may gzip the envelope. */
    private static String text(LoggedRequest request) {
        byte[] bytes = request.getBody();
        if (bytes.length == 0 || (bytes[0] & 0xff) != GZIP_MAGIC_FIRST_BYTE) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        try (GZIPInputStream unzipped = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(unzipped.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
