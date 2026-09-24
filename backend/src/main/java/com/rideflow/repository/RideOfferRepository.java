package com.rideflow.repository;

import com.rideflow.entity.OfferStatus;
import com.rideflow.entity.RideOffer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RideOfferRepository extends JpaRepository<RideOffer, UUID> {

    /**
     * Inserts a pending offer unless it would violate a unique index: the driver already holds a pending
     * offer (possibly from a concurrent round for another ride) or already got this ride. Returns 1 if
     * inserted, 0 if skipped, so races resolve in the database instead of failing the transaction.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO ride_offers (id, ride_id, driver_id, round, distance_m, status, offered_at, expires_at)
            VALUES (gen_random_uuid(), :rideId, :driverId, :round, :distanceMeters, 'PENDING', :offeredAt, :expiresAt)
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(
            @Param("rideId") UUID rideId,
            @Param("driverId") UUID driverId,
            @Param("round") int round,
            @Param("distanceMeters") int distanceMeters,
            @Param("offeredAt") Instant offeredAt,
            @Param("expiresAt") Instant expiresAt);

    Optional<RideOffer> findByRideIdAndDriverId(UUID rideId, UUID driverId);

    boolean existsByRideIdAndDriverId(UUID rideId, UUID driverId);

    List<RideOffer> findByRideIdAndStatus(UUID rideId, OfferStatus status);

    @Query("select o.driverId from RideOffer o where o.rideId = :rideId and o.status = :status")
    List<UUID> findDriverIdsByRideIdAndStatus(@Param("rideId") UUID rideId, @Param("status") OfferStatus status);

    List<RideOffer> findByDriverIdAndStatusAndExpiresAtAfterOrderByOfferedAtDesc(
            UUID driverId, OfferStatus status, Instant now);

    boolean existsByRideIdAndStatusAndExpiresAtAfter(UUID rideId, OfferStatus status, Instant now);

    /** Per ride (not global) so concurrent rounds for different rides never contend on the same rows. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update RideOffer o set o.status = :expired
            where o.rideId = :rideId and o.status = :pending and o.expiresAt <= :now
            """)
    int expireOverdueForRide(@Param("rideId") UUID rideId,
                             @Param("now") Instant now,
                             @Param("pending") OfferStatus pending,
                             @Param("expired") OfferStatus expired);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RideOffer o set o.status = :cancelled, o.respondedAt = :now
            where o.rideId = :rideId and o.status = :pending
            """)
    int cancelPendingForRide(@Param("rideId") UUID rideId,
                             @Param("now") Instant now,
                             @Param("pending") OfferStatus pending,
                             @Param("cancelled") OfferStatus cancelled);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RideOffer o set o.status = :cancelled, o.respondedAt = :now
            where o.driverId = :driverId and o.status = :pending
            """)
    int cancelPendingForDriver(@Param("driverId") UUID driverId,
                               @Param("now") Instant now,
                               @Param("pending") OfferStatus pending,
                               @Param("cancelled") OfferStatus cancelled);

    default int expireOverdueForRide(UUID rideId, Instant now) {
        return expireOverdueForRide(rideId, now, OfferStatus.PENDING, OfferStatus.EXPIRED);
    }

    default int cancelPendingForRide(UUID rideId, Instant now) {
        return cancelPendingForRide(rideId, now, OfferStatus.PENDING, OfferStatus.CANCELLED);
    }

    default int cancelPendingForDriver(UUID driverId, Instant now) {
        return cancelPendingForDriver(driverId, now, OfferStatus.PENDING, OfferStatus.CANCELLED);
    }
}
