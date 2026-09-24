package com.rideflow.dto.geo;

import com.rideflow.geospatial.GeoPoint;

/** A place to show in search results or as the label of a map pin. */
public record PlaceResponse(String name, String address, GeoPoint point) {
}
