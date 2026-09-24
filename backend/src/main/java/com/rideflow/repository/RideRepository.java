package com.rideflow.repository;

import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RideRepository extends JpaRepository<Ride, UUID> {

    /** Serialises matching rounds for one ride across threads and instances. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Ride r where r.id = :id")
    Optional<Ride> findByIdForUpdate(@Param("id") UUID id);

    Optional<Ride> findFirstByPassengerIdAndStatusIn(UUID passengerId, Collection<RideStatus> statuses);

    Optional<Ride> findFirstByDriverIdAndStatusIn(UUID driverId, Collection<RideStatus> statuses);

    Page<Ride> findByPassengerId(UUID passengerId, Pageable pageable);

    Page<Ride> findByPassengerIdAndStatus(UUID passengerId, RideStatus status, Pageable pageable);

    Page<Ride> findByDriverId(UUID driverId, Pageable pageable);

    Page<Ride> findByDriverIdAndStatus(UUID driverId, RideStatus status, Pageable pageable);

    /**
     * Rides waiting for a driver whose current matching round is over (or never started because its trigger
     * was lost) and that have no offer still open. Candidates only: the matching round re-checks under lock.
     */
    @Query("""
            select r.id from Ride r
            where r.status in :statuses
              and ((r.roundStartedAt is null and r.requestedAt <= :triggerCutoff) or r.roundStartedAt <= :roundCutoff)
              and not exists (
                  select o.id from RideOffer o
                  where o.rideId = r.id and o.status = com.rideflow.entity.OfferStatus.PENDING and o.expiresAt > :now)
            order by r.requestedAt
            """)
    List<UUID> findRidesDueForNextRound(
            @Param("statuses") Collection<RideStatus> statuses,
            @Param("triggerCutoff") Instant triggerCutoff,
            @Param("roundCutoff") Instant roundCutoff,
            @Param("now") Instant now,
            Pageable limit);

    /** Surge demand: open requests near a point, served by the partial GiST index {@code ix_rides_pickup_open}. */
    @Query(nativeQuery = true, value = """
            SELECT count(*) FROM rides
            WHERE status IN ('REQUESTED', 'MATCHING')
              AND requested_at > :since
              AND ST_DWithin(pickup_location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius)
            """)
    long countOpenRequestsNear(
            @Param("lat") double lat, @Param("lng") double lng, @Param("radius") double radiusMeters,
            @Param("since") Instant since);
}
