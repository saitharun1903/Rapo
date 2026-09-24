package com.rideflow.geospatial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.rideflow.config.RoutingProperties;
import com.rideflow.entity.EstimateSource;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RoutingTest {

    private static final GeoPoint HITECH_CITY = new GeoPoint(17.4435, 78.3772);
    private static final GeoPoint HUSSAIN_SAGAR = new GeoPoint(17.4239, 78.4738);
    private static final RoutingProperties PROPERTIES = new RoutingProperties(RoutingProperties.Provider.OSRM,
            "http://osrm.test", Duration.ofSeconds(1), Duration.ofSeconds(1), 1.3, 24);

    @Test
    void haversineMatchesKnownDistance() {
        // Hitech City to Hussain Sagar is ~10.4 km as the crow flies.
        assertThat(GeoMath.haversineMeters(HITECH_CITY, HUSSAIN_SAGAR)).isCloseTo(10_440, within(150.0));
        assertThat(GeoMath.haversineMeters(HITECH_CITY, HITECH_CITY)).isZero();
    }

    @Test
    void straightLineAppliesCircuityAndAverageSpeed() {
        RouteEstimate estimate = new StraightLineRoutingProvider(PROPERTIES).route(HITECH_CITY, HUSSAIN_SAGAR);

        double expectedDistance = GeoMath.haversineMeters(HITECH_CITY, HUSSAIN_SAGAR) * 1.3;
        assertThat(estimate.distanceMeters()).isCloseTo((int) expectedDistance, within(1));
        assertThat(estimate.durationSeconds()).isCloseTo((int) (expectedDistance / (24 / 3.6)), within(1));
        assertThat(estimate.source()).isEqualTo(EstimateSource.APPROXIMATE);
    }

    @Test
    void osrmResponseIsParsedWithLongitudeLatitudeOrder() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://osrm.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "http://osrm.test/route/v1/driving/78.377200,17.443500;78.473800,17.423900?overview=simplified&geometries=geojson"))
                .andRespond(withSuccess("""
                        {"code":"Ok","routes":[{"distance":12840.4,"duration":1719.6,
                          "geometry":{"type":"LineString","coordinates":[[78.3772,17.4435],[78.42,17.43],[78.4738,17.4239]]},
                          "legs":[]}],"waypoints":[]}
                        """, MediaType.APPLICATION_JSON));

        RouteEstimate estimate = new OsrmRoutingProvider(builder.build()).route(HITECH_CITY, HUSSAIN_SAGAR);

        assertThat(estimate.distanceMeters()).isEqualTo(12_840);
        assertThat(estimate.durationSeconds()).isEqualTo(1_720);
        assertThat(estimate.source()).isEqualTo(EstimateSource.ROUTED);
        assertThat(estimate.path()).hasSize(3).first().isEqualTo(HITECH_CITY);
        server.verify();
    }

    @Test
    void osrmNoRouteIsUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://osrm.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://osrm.test/route")))
                .andRespond(withSuccess("{\"code\":\"NoRoute\",\"routes\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new OsrmRoutingProvider(builder.build()).route(HITECH_CITY, HUSSAIN_SAGAR))
                .isInstanceOf(RoutingUnavailableException.class);
    }

    @Test
    void routingServiceFallsBackWhenPrimaryFails() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://osrm.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://osrm.test/route"))).andRespond(withServerError());
        RoutingService service = new RoutingService(new OsrmRoutingProvider(builder.build()),
                new StraightLineRoutingProvider(PROPERTIES));

        RouteEstimate estimate = service.route(HITECH_CITY, HUSSAIN_SAGAR);

        assertThat(estimate.source()).isEqualTo(EstimateSource.APPROXIMATE);
        assertThat(estimate.distanceMeters()).isPositive();
    }
}
