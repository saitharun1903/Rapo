package com.rideflow.geospatial;

import java.util.List;
import java.util.Optional;

/** Used when {@code rideflow.geocoding.provider=disabled}: every call fails as "unavailable". */
public class DisabledGeocodingProvider implements GeocodingProvider {

    private static final String DISABLED = "Geocoding is disabled (rideflow.geocoding.provider=disabled)";

    @Override
    public List<Place> search(String query, GeoPoint near, int limit) {
        throw new GeocodingUnavailableException(DISABLED);
    }

    @Override
    public Optional<Place> reverse(GeoPoint point) {
        throw new GeocodingUnavailableException(DISABLED);
    }
}
