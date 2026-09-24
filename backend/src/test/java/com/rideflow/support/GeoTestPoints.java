package com.rideflow.support;

import com.rideflow.geospatial.GeoPoint;

/** Real Hyderabad coordinates and a helper to place points at known offsets. */
public final class GeoTestPoints {

    public static final GeoPoint HITECH_CITY = new GeoPoint(17.4435, 78.3772);
    public static final GeoPoint HUSSAIN_SAGAR = new GeoPoint(17.4239, 78.4738);

    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    private GeoTestPoints() {
    }

    /** A point {@code northMeters} north and {@code eastMeters} east of {@code origin} (small offsets). */
    public static GeoPoint offset(GeoPoint origin, double northMeters, double eastMeters) {
        double lat = origin.lat() + northMeters / METERS_PER_DEGREE_LAT;
        double lng = origin.lng() + eastMeters / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(origin.lat())));
        return new GeoPoint(lat, lng);
    }

    /** Evenly spaced points from {@code from} to {@code to}, both ends included. */
    public static GeoPoint[] line(GeoPoint from, GeoPoint to, int points) {
        GeoPoint[] line = new GeoPoint[points];
        for (int i = 0; i < points; i++) {
            double t = (double) i / (points - 1);
            line[i] = new GeoPoint(from.lat() + (to.lat() - from.lat()) * t, from.lng() + (to.lng() - from.lng()) * t);
        }
        return line;
    }
}
