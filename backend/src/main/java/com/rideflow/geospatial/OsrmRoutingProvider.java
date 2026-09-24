package com.rideflow.geospatial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rideflow.entity.EstimateSource;
import java.util.List;
import java.util.Locale;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Road routing via the OSRM HTTP API ({@code /route/v1/driving/{lng},{lat};{lng},{lat}}). The public demo
 * server is for light development use only; point {@code ROUTING_BASE_URL} at a self-hosted OSRM for
 * anything heavier.
 */
public class OsrmRoutingProvider implements RoutingProvider {

    private static final String OK = "Ok";
    private static final int LNG = 0;
    private static final int LAT = 1;

    private final RestClient client;

    public OsrmRoutingProvider(RestClient client) {
        this.client = client;
    }

    @Override
    public RouteEstimate route(GeoPoint from, GeoPoint to) {
        OsrmResponse response;
        try {
            response = client.get()
                    // One variable per number: the ',' and ';' separators must stay literal (not percent-encoded).
                    .uri("/route/v1/driving/{fromLng},{fromLat};{toLng},{toLat}?overview=simplified&geometries=geojson",
                            coordinate(from.lng()), coordinate(from.lat()), coordinate(to.lng()), coordinate(to.lat()))
                    .retrieve()
                    .body(OsrmResponse.class);
        } catch (RestClientException ex) {
            throw new RoutingUnavailableException("OSRM request failed: " + ex.getMessage(), ex);
        }
        if (response == null || !OK.equals(response.code()) || response.routes() == null || response.routes().isEmpty()) {
            throw new RoutingUnavailableException("OSRM returned no route (code " + (response == null ? null : response.code()) + ")");
        }
        OsrmRoute route = response.routes().getFirst();
        List<GeoPoint> path = route.geometry() == null || route.geometry().coordinates() == null
                ? List.of(from, to)
                : route.geometry().coordinates().stream().map(c -> new GeoPoint(c.get(LAT), c.get(LNG))).toList();
        return new RouteEstimate(
                (int) Math.round(route.distance()), (int) Math.round(route.duration()), path, EstimateSource.ROUTED);
    }

    private static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmResponse(String code, List<OsrmRoute> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmRoute(double distance, double duration, OsrmGeometry geometry) {
    }

    /** GeoJSON LineString: coordinates are [longitude, latitude] pairs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmGeometry(List<List<Double>> coordinates) {
    }
}
