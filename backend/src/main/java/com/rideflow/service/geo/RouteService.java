package com.rideflow.service.geo;

import com.rideflow.cache.CacheName;
import com.rideflow.cache.JsonCache;
import com.rideflow.cache.RedisKeys;
import com.rideflow.entity.EstimateSource;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.geospatial.RouteEstimate;
import com.rideflow.geospatial.RoutingService;
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

    public RouteService(RoutingService routing, JsonCache cache) {
        this.routing = routing;
        this.cache = cache;
    }

    public RouteEstimate route(GeoPoint from, GeoPoint to) {
        return cache.getOrCompute(CacheName.ROUTE, RedisKeys.route(from, to), RouteEstimate.class,
                () -> routing.route(from, to), route -> route.source() == EstimateSource.ROUTED);
    }
}
