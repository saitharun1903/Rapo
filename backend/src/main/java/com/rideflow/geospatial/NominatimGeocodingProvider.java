package com.rideflow.geospatial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Geocoding via the Nominatim API ({@code /search}, {@code /reverse}, format {@code jsonv2}). The caller
 * (GeocodingService) enforces the public server's one-request-per-second limit and caches results; this class
 * only speaks HTTP. Query values are passed as URI variables so they are always fully percent-encoded.
 */
public class NominatimGeocodingProvider implements GeocodingProvider {

    /** Street-level detail for reverse lookups. */
    private static final int REVERSE_ZOOM = 18;
    private static final String FORMAT = "jsonv2";
    private static final String LANGUAGE = "en";

    private final RestClient client;
    private final String countryCodes;
    private final double biasBoxDegrees;

    public NominatimGeocodingProvider(RestClient client, String countryCodes, double biasBoxDegrees) {
        this.client = client;
        this.countryCodes = countryCodes;
        this.biasBoxDegrees = biasBoxDegrees;
    }

    @Override
    public List<Place> search(String query, GeoPoint near, int limit) {
        // viewbox is left,top,right,bottom (lng,lat,lng,lat); bounded=0 prefers results inside it without excluding others.
        String viewbox = String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f", near.lng() - biasBoxDegrees,
                near.lat() + biasBoxDegrees, near.lng() + biasBoxDegrees, near.lat() - biasBoxDegrees);
        NominatimPlace[] results;
        try {
            results = client.get()
                    .uri(builder -> builder.path("/search")
                            .queryParam("q", "{q}")
                            .queryParam("format", FORMAT)
                            .queryParam("limit", "{limit}")
                            .queryParam("countrycodes", "{countryCodes}")
                            .queryParam("viewbox", "{viewbox}")
                            .queryParam("bounded", 0)
                            .queryParam("accept-language", LANGUAGE)
                            .build(Map.of("q", query, "limit", limit, "countryCodes", countryCodes, "viewbox", viewbox)))
                    .retrieve()
                    .body(NominatimPlace[].class);
        } catch (RestClientException ex) {
            throw new GeocodingUnavailableException("Nominatim search failed: " + ex.getMessage(), ex);
        }
        try {
            return results == null ? List.of() : Arrays.stream(results).map(NominatimPlace::toPlace).toList();
        } catch (NumberFormatException ex) {
            throw new GeocodingUnavailableException("Nominatim returned an unreadable coordinate", ex);
        }
    }

    @Override
    public Optional<Place> reverse(GeoPoint point) {
        NominatimPlace result;
        try {
            result = client.get()
                    .uri(builder -> builder.path("/reverse")
                            .queryParam("lat", "{lat}")
                            .queryParam("lon", "{lon}")
                            .queryParam("format", FORMAT)
                            .queryParam("zoom", REVERSE_ZOOM)
                            .queryParam("accept-language", LANGUAGE)
                            .build(Map.of("lat", coordinate(point.lat()), "lon", coordinate(point.lng()))))
                    .retrieve()
                    .body(NominatimPlace.class);
        } catch (RestClientException ex) {
            throw new GeocodingUnavailableException("Nominatim reverse lookup failed: " + ex.getMessage(), ex);
        }
        // Nominatim answers 200 with {"error": "..."} when nothing is found (for example at sea).
        if (result == null || result.displayName() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(result.toPlace());
        } catch (NumberFormatException ex) {
            throw new GeocodingUnavailableException("Nominatim returned an unreadable coordinate", ex);
        }
    }

    private static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    /** Nominatim sends coordinates as strings. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record NominatimPlace(String lat, String lon, String name, @JsonProperty("display_name") String displayName) {

        Place toPlace() {
            String shortName = name == null || name.isBlank() ? displayName.split(",", 2)[0].trim() : name;
            return new Place(shortName, displayName, new GeoPoint(Double.parseDouble(lat), Double.parseDouble(lon)));
        }
    }
}
