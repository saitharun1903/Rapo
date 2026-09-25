package com.rideflow.service.geo;

import com.rideflow.cache.CacheName;
import com.rideflow.cache.JsonCache;
import com.rideflow.cache.RateLimitScope;
import com.rideflow.cache.RateLimiter;
import com.rideflow.cache.RedisKeys;
import com.rideflow.entity.EstimateSource;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.geospatial.RouteEstimate;
import com.rideflow.geospatial.RoutingService;
import com.rideflow.service.ride.ServiceAreaPolicy;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Routes between places a user picked (fare estimates, route preview), cached in Redis. Such pairs repeat:
 * places come from search results and map pins, and a passenger usually re-estimates before booking. The
 * router is a slow external call, so a hit saves most of an estimate's latency.
 *
 * <p>Only {@code ROUTED} answers are stored. The straight-line fallback is served but not cached, so a
 * router outage does not keep degraded estimates for the whole TTL. Routes from a driver's live position
 * are not cached here: they almost never repeat (see {@code LiveEtaService} for how those are throttled).
 */
@Service
public class RouteService {

    private final RoutingService routing;
    private final JsonCache cache;
    private final ServiceAreaPolicy serviceArea;
    private final RateLimiter rateLimiter;

    public RouteService(RoutingService routing, JsonCache cache, ServiceAreaPolicy serviceArea, RateLimiter rateLimiter) {
        this.routing = routing;
        this.cache = cache;
        this.serviceArea = serviceArea;
        this.rateLimiter = rateLimiter;
    }

    /**
     * A route a user asked for (the apps' route previews): only within reach of the service area, and rate
     * limited per user, since each cache miss is a call to the public router.
     */
    public RouteEstimate preview(UUID userId, GeoPoint from, GeoPoint to) {
        serviceArea.validateRoute(from, to);
        rateLimiter.acquire(RateLimitScope.ROUTE, userId.toString());
        return route(from, to);
    }

    public RouteEstimate route(GeoPoint from, GeoPoint to) {
        return cache.getOrCompute(CacheName.ROUTE, RedisKeys.route(from, to), RouteEstimate.class,
                () -> routing.route(from, to), route -> route.source() == EstimateSource.ROUTED);
    }
}
