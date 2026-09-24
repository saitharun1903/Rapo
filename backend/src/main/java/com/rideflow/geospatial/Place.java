package com.rideflow.geospatial;

/**
 * A geocoding result.
 *
 * @param name    short name (for example a landmark), or the first part of the address when the place has none
 * @param address full display address
 */
public record Place(String name, String address, GeoPoint point) {
}
