package com.rideflow.geospatial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes with the configured provider and degrades to the straight-line estimate if it fails, so fare
 * estimates keep working when the router is down. The result's {@code source} tells callers which was used.
 */
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final RoutingProvider primary;
    private final RoutingProvider fallback;

    public RoutingService(RoutingProvider primary, RoutingProvider fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    public RouteEstimate route(GeoPoint from, GeoPoint to) {
        if (primary == fallback) {
            return fallback.route(from, to);
        }
        try {
            return primary.route(from, to);
        } catch (RoutingUnavailableException ex) {
            log.warn("Routing provider unavailable, using straight-line estimate: {}", ex.getMessage());
            return fallback.route(from, to);
        }
    }
}
