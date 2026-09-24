package com.rideflow.geospatial;

import java.util.List;
import java.util.Optional;

/** Turns text into places and points into addresses. Implementations throw {@link GeocodingUnavailableException}. */
public interface GeocodingProvider {

    /** Places matching {@code query}, best first, biased towards {@code near}. */
    List<Place> search(String query, GeoPoint near, int limit);

    /** The address at {@code point}, if any. */
    Optional<Place> reverse(GeoPoint point);
}
