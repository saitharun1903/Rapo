package com.rideflow.service.ride;

import com.rideflow.cache.CacheName;
import com.rideflow.cache.JsonCache;
import com.rideflow.cache.RedisKeys;
import com.rideflow.config.CacheProperties;
import com.rideflow.dto.ride.EtaResponse;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.geospatial.RouteEstimate;
import com.rideflow.geospatial.RoutingService;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The driver's ETA to the next stop, kept in Redis ({@code ride:{id}:eta}, TTL 30 s). A driver reports a
 * position every few seconds; routing each one would call the router several times a second per ride for a
 * number that barely changes. Instead the cached ETA rides along with location pushes, and when it expires
 * one instance (holding {@code ride:{id}:eta-refresh}) recomputes it in the background.
 *
 * <p>Invalidation: every status change deletes the entry, because the destination changes (pickup, then
 * dropoff) or tracking ends. An ETA computed for the old destination and written after that delete is
 * ignored on read, since entries carry their target.
 */
@Service
public class LiveEtaService {

    private final JsonCache cache;
    private final RoutingService routing;
    private final CacheProperties properties;
    private final Clock clock;

    public LiveEtaService(JsonCache cache, RoutingService routing, CacheProperties properties, Clock clock) {
        this.cache = cache;
        this.routing = routing;
        this.properties = properties;
        this.clock = clock;
    }

    /** The cached ETA for this destination, if fresh. Never calls the router. */
    public Optional<EtaResponse> cached(UUID rideId, EtaDestination destination) {
        return cache.peek(CacheName.ETA, RedisKeys.eta(rideId), EtaResponse.class)
                .filter(eta -> eta.target() == destination.target());
    }

    /** Recomputes in the background unless another caller already is (or did, within the TTL). */
    @Async
    public void refreshInBackground(UUID rideId, GeoPoint driverPosition, EtaDestination destination) {
        if (cache.claim(RedisKeys.etaRefresh(rideId), CacheName.ETA.ttl(properties))) {
            cache.put(CacheName.ETA, RedisKeys.eta(rideId), compute(driverPosition, destination));
        }
    }

    /** For the tracking snapshot: the cached ETA, or a fresh one (which is then cached). */
    public EtaResponse current(UUID rideId, GeoPoint driverPosition, EtaDestination destination) {
        return cached(rideId, destination).orElseGet(() -> {
            EtaResponse eta = compute(driverPosition, destination);
            cache.put(CacheName.ETA, RedisKeys.eta(rideId), eta);
            return eta;
        });
    }

    /** Called with every ride status change; the entry is deleted once the change has committed. */
    public void evictAfterCommit(UUID rideId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.evict(RedisKeys.eta(rideId), RedisKeys.etaRefresh(rideId));
            }
        });
    }

    private EtaResponse compute(GeoPoint from, EtaDestination destination) {
        RouteEstimate route = routing.route(from, destination.point());
        return new EtaResponse(destination.target(), route.durationSeconds(), route.distanceMeters(),
                route.source(), clock.instant());
    }
}
