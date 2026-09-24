package com.rideflow.geospatial;

/**
 * Standard geohash (base32, interleaved longitude/latitude bits). Precision 6 is a cell of roughly
 * 1.2 km × 0.6 km, used to share one surge value between nearby pickups.
 */
public final class Geohash {

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
    private static final int BITS_PER_CHAR = 5;
    private static final double MAX_LAT = 90.0;
    private static final double MAX_LNG = 180.0;

    private Geohash() {
    }

    public static String encode(GeoPoint point, int precision) {
        double[] lat = {-MAX_LAT, MAX_LAT};
        double[] lng = {-MAX_LNG, MAX_LNG};
        StringBuilder hash = new StringBuilder(precision);
        boolean evenBit = true;
        int bit = 0;
        int ch = 0;
        while (hash.length() < precision) {
            double[] range = evenBit ? lng : lat;
            double value = evenBit ? point.lng() : point.lat();
            double mid = (range[0] + range[1]) / 2;
            ch <<= 1;
            if (value >= mid) {
                ch |= 1;
                range[0] = mid;
            } else {
                range[1] = mid;
            }
            evenBit = !evenBit;
            if (++bit == BITS_PER_CHAR) {
                hash.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }
        return hash.toString();
    }

    /** Centre of the cell. */
    public static GeoPoint center(String hash) {
        double[] lat = {-MAX_LAT, MAX_LAT};
        double[] lng = {-MAX_LNG, MAX_LNG};
        boolean evenBit = true;
        for (char c : hash.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Not a geohash: " + hash);
            }
            for (int shift = BITS_PER_CHAR - 1; shift >= 0; shift--) {
                double[] range = evenBit ? lng : lat;
                double mid = (range[0] + range[1]) / 2;
                if (((value >> shift) & 1) == 1) {
                    range[0] = mid;
                } else {
                    range[1] = mid;
                }
                evenBit = !evenBit;
            }
        }
        return new GeoPoint((lat[0] + lat[1]) / 2, (lng[0] + lng[1]) / 2);
    }
}
