package com.rideflow.integration;

import static com.rideflow.support.GeoTestPoints.HITECH_CITY;
import static com.rideflow.support.GeoTestPoints.HUSSAIN_SAGAR;
import static com.rideflow.support.GeoTestPoints.offset;
import static com.rideflow.support.RideApi.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.rideflow.cache.RedisKeys;
import com.rideflow.entity.EstimateSource;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.geospatial.Geohash;
import com.rideflow.support.IntegrationTestContainers;
import com.rideflow.support.RideApi;
import com.rideflow.support.RideFixtures;
import com.rideflow.support.RideFixtures.Actor;
import com.rideflow.support.RideTestConfig;
import com.rideflow.support.StubProvidersConfig;
import com.rideflow.support.StubProvidersConfig.CountingGeocoder;
import com.rideflow.support.StubProvidersConfig.CountingRouter;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The Redis caches against a real Redis: what is stored, for how long, what is deliberately not stored,
 * and when entries are invalidated. Upstream providers are counting stand-ins, so a saved call is visible.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({RideTestConfig.class, StubProvidersConfig.class})
class RedisCachingIT extends IntegrationTestContainers {

    private static final Duration AWAIT = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RideFixtures fixtures;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private CountingRouter router;
    @Autowired
    private CountingGeocoder geocoder;

    private RideApi api;

    @BeforeEach
    void setUp() {
        fixtures.reset();
        router.reset();
        geocoder.reset();
        api = new RideApi(mvc);
    }

    private DocumentContext estimate(Actor passenger, GeoPoint pickup, GeoPoint dropoff) throws Exception {
        MvcResult result = api.call(passenger, "POST", "/api/fares/estimate",
                "{\"pickup\":%s,\"dropoff\":%s}".formatted(RideApi.point(pickup), RideApi.point(dropoff)));
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(200);
        return JsonPath.parse(body(result));
    }

    private long ttlSeconds(String key) {
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        return ttl == null ? -2 : ttl;
    }

    @Test
    void roadRoutesAreCachedForFifteenMinutesButFallbackRoutesAreNot() throws Exception {
        Actor passenger = fixtures.passenger();

        estimate(passenger, HITECH_CITY, HUSSAIN_SAGAR);
        DocumentContext second = estimate(passenger, HITECH_CITY, HUSSAIN_SAGAR);

        assertThat(router.calls()).isEqualTo(1);
        assertThat(second.read("$.estimateSource", String.class)).isEqualTo("ROUTED");
        assertThat(ttlSeconds(RedisKeys.route(HITECH_CITY, HUSSAIN_SAGAR))).isBetween(14 * 60L, 15 * 60L);

        // A degraded (straight-line) answer is served but not kept: the next request tries the router again.
        router.answerAs(EstimateSource.APPROXIMATE);
        GeoPoint otherPickup = offset(HITECH_CITY, 500, 0);
        estimate(passenger, otherPickup, HUSSAIN_SAGAR);
        estimate(passenger, otherPickup, HUSSAIN_SAGAR);

        assertThat(router.calls()).isEqualTo(3);
        assertThat(redis.hasKey(RedisKeys.route(otherPickup, HUSSAIN_SAGAR))).isFalse();
    }

    @Test
    void surgeIsCachedPerCellForAMinuteAndServedFromRedis() throws Exception {
        Actor passenger = fixtures.passenger();
        String key = RedisKeys.surge(Geohash.encode(HITECH_CITY, 6));

        DocumentContext first = estimate(passenger, HITECH_CITY, HUSSAIN_SAGAR);

        assertThat(redis.opsForValue().get(key)).isEqualTo(first.read("$.surgeMultiplier", String.class));
        assertThat(ttlSeconds(key)).isBetween(1L, 60L);

        // Estimates read the cached value: a pickup 50 m away in the same cell gets what Redis holds.
        redis.opsForValue().set(key, "1.70", Duration.ofSeconds(60));
        DocumentContext cached = estimate(passenger, offset(HITECH_CITY, 50, 0), HUSSAIN_SAGAR);
        assertThat(cached.read("$.surgeMultiplier", String.class)).isEqualTo("1.70");
    }

    @Test
    void geocodingResultsAreCachedForADayUnderHashedKeys() throws Exception {
        Actor passenger = fixtures.passenger();

        MvcResult first = api.call(passenger, "GET", "/api/geo/search?q=Charminar", null);
        // MockMvc encodes the URI itself: pass the raw text a user would type.
        MvcResult second = api.call(passenger, "GET", "/api/geo/search?q=  CHARMINAR ", null);

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(second)).isEqualTo(body(first));
        assertThat(JsonPath.<String>read(body(first), "$[0].name")).isEqualTo("Charminar");
        assertThat(geocoder.calls()).isEqualTo(1);

        Set<String> keys = redis.keys("geocode:*");
        assertThat(keys).hasSize(1).allSatisfy(key -> assertThat(key).doesNotContainIgnoringCase("charminar"));
        assertThat(ttlSeconds(keys.iterator().next())).isBetween(23 * 3600L, 24 * 3600L);

        // Reverse lookups a few metres apart share one entry.
        api.call(passenger, "GET", "/api/geo/reverse?lat=17.41230&lng=78.44810", null);
        api.call(passenger, "GET", "/api/geo/reverse?lat=17.41232&lng=78.44812", null);
        assertThat(geocoder.calls()).isEqualTo(2);
    }

    @Test
    void liveEtaIsCachedForThirtySecondsAndEvictedWhenTheRideMovesOn() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor driver = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        assertThat(api.goOnline(driver, offset(HITECH_CITY, 800, 0)).getResponse().getStatus()).isEqualTo(200);
        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);
        await().atMost(AWAIT).until(() -> api.openOfferCount(driver) == 1);
        assertThat(api.call(driver, "POST", "/api/rides/" + rideId + "/accept", null).getResponse().getStatus())
                .isEqualTo(200);

        DocumentContext tracking = JsonPath.parse(body(api.call(passenger, "GET", "/api/rides/" + rideId + "/tracking", null)));
        assertThat(tracking.read("$.eta.target", String.class)).isEqualTo("PICKUP");
        assertThat(tracking.read("$.eta.source", String.class)).isEqualTo("ROUTED");
        assertThat(ttlSeconds(RedisKeys.eta(rideId))).isBetween(1L, 30L);

        int routerCallsBefore = router.calls();
        api.call(passenger, "GET", "/api/rides/" + rideId + "/tracking", null);
        assertThat(router.calls()).isEqualTo(routerCallsBefore);

        // Any status change invalidates it (after arrival there is no ETA; after start it targets the dropoff).
        assertThat(api.call(driver, "POST", "/api/rides/" + rideId + "/en-route", null).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(redis.hasKey(RedisKeys.eta(rideId))).isFalse();
    }
}
