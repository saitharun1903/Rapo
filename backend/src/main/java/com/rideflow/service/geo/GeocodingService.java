package com.rideflow.service.geo;

import com.rideflow.cache.CacheName;
import com.rideflow.cache.JsonCache;
import com.rideflow.cache.RateLimitScope;
import com.rideflow.cache.RateLimiter;
import com.rideflow.cache.RedisKeys;
import com.rideflow.config.GeocodingProperties;
import com.rideflow.config.RideProperties;
import com.rideflow.dto.geo.PlaceResponse;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RetryableException;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.geospatial.GeocodingProvider;
import com.rideflow.geospatial.GeocodingUnavailableException;
import com.rideflow.geospatial.Geohash;
import com.rideflow.geospatial.Place;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Place search and reverse geocoding, in front of an external provider:
 * <ul>
 *   <li>per-user rate limit ({@code GEOCODING});</li>
 *   <li>Redis cache for 24 h, keyed by the normalised query and a coarse bias cell (geohash 4, ~40 km), so
 *       the same search from anywhere in the city is one upstream call;</li>
 *   <li>a global limit of one upstream request per second ({@code GEOCODING_UPSTREAM}), as the public
 *       Nominatim policy requires. Only cache misses count against it.</li>
 * </ul>
 * Nominatim's policy also forbids search-as-you-type, so clients search on submit.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final int BIAS_CELL_PRECISION = 4;
    /** Reverse lookups are rounded to ~11 m, so nearby map pins share one cache entry. */
    private static final double REVERSE_ROUNDING = 1e4;
    private static final String UPSTREAM_SUBJECT = "provider";
    private static final Duration UNAVAILABLE_RETRY_AFTER = Duration.ofSeconds(5);

    private final GeocodingProvider provider;
    private final JsonCache cache;
    private final RateLimiter rateLimiter;
    private final GeocodingProperties properties;
    private final GeoPoint serviceAreaCenter;

    public GeocodingService(GeocodingProvider provider, JsonCache cache, RateLimiter rateLimiter,
                            GeocodingProperties properties, RideProperties rideProperties) {
        this.provider = provider;
        this.cache = cache;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.serviceAreaCenter = rideProperties.serviceArea().center();
    }

    /** Stored as one JSON value per query; an empty list is a valid, cacheable answer. */
    record SearchResults(List<PlaceResponse> places) {
    }

    /**
     * @param near where the user is, to prefer nearby results; the service-area centre when unknown
     */
    public List<PlaceResponse> search(UUID userId, String query, GeoPoint near) {
        rateLimiter.acquire(RateLimitScope.GEOCODING, userId.toString());
        String normalized = query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String biasCell = Geohash.encode(near == null ? serviceAreaCenter : near, BIAS_CELL_PRECISION);
        int limit = properties.resultLimit();
        SearchResults results = cache.getOrCompute(CacheName.GEOCODE,
                RedisKeys.geocodeSearch(normalized, biasCell, limit), SearchResults.class,
                () -> upstream(() -> new SearchResults(provider.search(normalized, Geohash.center(biasCell), limit)
                        .stream().map(GeocodingService::toResponse).toList())),
                any -> true);
        return results.places();
    }

    public Optional<PlaceResponse> reverse(UUID userId, GeoPoint point) {
        rateLimiter.acquire(RateLimitScope.GEOCODING, userId.toString());
        GeoPoint rounded = new GeoPoint(Math.round(point.lat() * REVERSE_ROUNDING) / REVERSE_ROUNDING,
                Math.round(point.lng() * REVERSE_ROUNDING) / REVERSE_ROUNDING);
        SearchResults results = cache.getOrCompute(CacheName.GEOCODE, RedisKeys.geocodeReverse(rounded),
                SearchResults.class,
                () -> upstream(() -> new SearchResults(provider.reverse(rounded).map(GeocodingService::toResponse)
                        .stream().toList())),
                any -> true);
        return results.places().stream().findFirst();
    }

    /** One upstream call, within the global request budget; provider failures become 503s. */
    private SearchResults upstream(Supplier<SearchResults> call) {
        Optional<Duration> wait = rateLimiter.tryAcquire(RateLimitScope.GEOCODING_UPSTREAM, UPSTREAM_SUBJECT);
        if (wait.isPresent()) {
            throw new RetryableException(ErrorCode.GEOCODING_UNAVAILABLE,
                    "Place search is busy; try again in a moment", wait.get());
        }
        try {
            return call.get();
        } catch (GeocodingUnavailableException ex) {
            log.warn("Geocoding provider unavailable: {}", ex.getMessage());
            throw new RetryableException(ErrorCode.GEOCODING_UNAVAILABLE,
                    "Place search is temporarily unavailable; pick the point on the map instead",
                    UNAVAILABLE_RETRY_AFTER);
        }
    }

    private static PlaceResponse toResponse(Place place) {
        return new PlaceResponse(place.name(), place.address(), place.point());
    }
}
