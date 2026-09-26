package com.rideflow.repository;

import com.rideflow.entity.RideMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RideMessageRepository extends JpaRepository<RideMessage, UUID> {

    List<RideMessage> findByRideIdOrderBySentAtAscIdAsc(UUID rideId);

    long countByRideId(UUID rideId);

    /**
     * Deletes the messages of rides that ended (completed, cancelled or expired) before {@code endedBefore}.
     * Rides still in progress keep their conversation whatever its age.
     */
    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM ride_messages m
             USING rides r
             WHERE m.ride_id = r.id
               AND COALESCE(r.completed_at, r.cancelled_at, r.expired_at) < :endedBefore
            """, nativeQuery = true)
    int deleteForRidesEndedBefore(@Param("endedBefore") Instant endedBefore);
}
