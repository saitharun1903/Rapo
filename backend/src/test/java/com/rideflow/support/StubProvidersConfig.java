package com.rideflow.support;

import com.rideflow.entity.EstimateSource;
import com.rideflow.geospatial.GeoMath;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.geospatial.GeocodingProvider;
import com.rideflow.geospatial.Place;
import com.rideflow.geospatial.RouteEstimate;
import com.rideflow.geospatial.RoutingProvider;
import com.rideflow.geospatial.RoutingService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the external router and geocoder with in-process stand-ins that count their calls, so tests can
 * prove when a cache saved an upstream call. Tests have no network, and the real providers are covered by
 * RoutingTest and NominatimGeocodingProviderTest against mock servers.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubProvidersConfig {

    /** Road distance is taken as 1.4 x straight line, at 7 m/s, only so answers are deterministic. */
    private static final double ROAD_FACTOR = 1.4;
    private static final double SPEED_MPS = 7.0;

    @Bean
    CountingRouter countingRouter() {
        return new CountingRouter();
    }

    @Bean
    @Primary
    RoutingService stubRoutingService(CountingRouter router) {
        return new RoutingService(router, router);
    }

    @Bean
    @Primary
    CountingGeocoder countingGeocoder() {
        return new CountingGeocoder();
    }

    public static final class CountingRouter implements RoutingProvider {

        private final AtomicInteger calls = new AtomicInteger();
        private volatile EstimateSource source = EstimateSource.ROUTED;

        @Override
        public RouteEstimate route(GeoPoint from, GeoPoint to) {
            calls.incrementAndGet();
            double meters = GeoMath.haversineMeters(from, to) * ROAD_FACTOR;
            return new RouteEstimate((int) Math.round(meters), (int) Math.round(meters / SPEED_MPS),
                    List.of(from, to), source);
        }

        public int calls() {
            return calls.get();
        }

        /** Makes the stand-in answer like the straight-line fallback does. */
        public void answerAs(EstimateSource source) {
            this.source = source;
        }

        public void reset() {
            calls.set(0);
            source = EstimateSource.ROUTED;
        }
    }

    public static final class CountingGeocoder implements GeocodingProvider {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public List<Place> search(String query, GeoPoint near, int limit) {
            calls.incrementAndGet();
            return List.of(new Place("Charminar", "Charminar, Hyderabad, Telangana, India", new GeoPoint(17.3616, 78.4747)));
        }

        @Override
        public Optional<Place> reverse(GeoPoint point) {
            calls.incrementAndGet();
            return Optional.of(new Place("Road No. 1", "Road No. 1, Banjara Hills, Hyderabad", point));
        }

        public int calls() {
            return calls.get();
        }

        public void reset() {
            calls.set(0);
        }
    }
}
