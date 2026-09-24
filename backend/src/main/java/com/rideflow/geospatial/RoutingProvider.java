package com.rideflow.geospatial;

/** A source of road routes. Implementations are replaceable through configuration (see RideDomainConfig). */
public interface RoutingProvider {

    /**
     * @throws RoutingUnavailableException if the provider cannot produce a route right now
     */
    RouteEstimate route(GeoPoint from, GeoPoint to);
}
