package com.rideflow.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideflow.geospatial.GeoPoint;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisKeysTest {

    @Test
    void routeKeysIgnoreDifferencesBelowAboutAMetre() {
        GeoPoint from = new GeoPoint(17.443500, 78.377200);
        GeoPoint jitter = new GeoPoint(17.4435004, 78.3771996);
        GeoPoint to = new GeoPoint(17.4239, 78.4738);

        assertThat(RedisKeys.route(from, to)).isEqualTo("route:17.44350,78.37720:17.42390,78.47380");
        assertThat(RedisKeys.route(jitter, to)).isEqualTo(RedisKeys.route(from, to));
        assertThat(RedisKeys.route(to, from)).isNotEqualTo(RedisKeys.route(from, to));
    }

    @Test
    void personalDataNeverAppearsInKeys() {
        String login = RedisKeys.rateLimit(RateLimitScope.LOGIN, "203.0.113.7|asha@example.com");
        String search = RedisKeys.geocodeSearch("12 banjara hills road", "tepg", 5);

        assertThat(login).startsWith("rl:login:").doesNotContain("asha").doesNotContain("203.0.113.7");
        assertThat(search).startsWith("geocode:search:").doesNotContain("banjara");
        assertThat(RedisKeys.rateLimit(RateLimitScope.LOGIN, "203.0.113.7|asha@example.com")).isEqualTo(login);
        assertThat(RedisKeys.rateLimit(RateLimitScope.LOGIN, "203.0.113.8|asha@example.com")).isNotEqualTo(login);
    }

    @Test
    void rideKeysAreScopedToTheRide() {
        UUID ride = UUID.fromString("0b8e5c1e-0000-4000-8000-000000000001");

        assertThat(RedisKeys.eta(ride)).isEqualTo("ride:0b8e5c1e-0000-4000-8000-000000000001:eta");
        assertThat(RedisKeys.etaRefresh(ride)).isEqualTo("ride:0b8e5c1e-0000-4000-8000-000000000001:eta-refresh");
        assertThat(RedisKeys.surge("tepg5x")).isEqualTo("surge:tepg5x");
    }
}
