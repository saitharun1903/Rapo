package com.rideflow.geospatial;

/**
 * In-memory great-circle distance for single point-to-point checks (fallback routing estimates).
 * Searches over many rows are always done in PostGIS, never by looping here.
 */
public final class GeoMath {

    /** Mean Earth radius (IUGG), metres. */
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoMath() {
    }

    public static double haversineMeters(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
