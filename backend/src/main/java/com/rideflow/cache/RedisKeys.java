package com.rideflow.cache;

import com.rideflow.geospatial.GeoPoint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Every Redis key the application writes (documented in docs/architecture.md section 9). Keys never contain
 * personal data in clear: free-text queries and rate-limit subjects (IP addresses, emails) are hashed.
 */
public final class RedisKeys {

    /** 5 decimal places is about 1 m: finer differences would only fragment the cache. */
    private static final String COORDINATE = "%.5f";
    private static final int HASH_HEX_CHARS = 32;

    private RedisKeys() {
    }

    public static String route(GeoPoint from, GeoPoint to) {
        return "route:" + point(from) + ":" + point(to);
    }

    public static String geocodeSearch(String normalizedQuery, String biasCell, int limit) {
        return "geocode:search:" + hash(normalizedQuery + "|" + biasCell + "|" + limit);
    }

    public static String geocodeReverse(GeoPoint point) {
        return "geocode:reverse:" + point(point);
    }

    public static String surge(String geohash) {
        return "surge:" + geohash;
    }

    public static String eta(UUID rideId) {
        return "ride:" + rideId + ":eta";
    }

    /** Held while one instance recomputes a ride's ETA, so concurrent location updates do not all call the router. */
    public static String etaRefresh(UUID rideId) {
        return "ride:" + rideId + ":eta-refresh";
    }

    /** A driver's availability and active ride, read on every location report. */
    public static String driverState(UUID driverId) {
        return "driver:" + driverId + ":state";
    }

    /** A driver's latest position (hash). */
    public static String driverLocation(UUID driverId) {
        return "driver:" + driverId + ":location";
    }

    public static String rateLimit(RateLimitScope scope, String subject) {
        return "rl:" + scope.name().toLowerCase(Locale.ROOT) + ":" + hash(subject);
    }

    private static String point(GeoPoint point) {
        return String.format(Locale.ROOT, COORDINATE + "," + COORDINATE, point.lat(), point.lng());
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_HEX_CHARS);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 must be available on every Java platform", ex);
        }
    }
}
