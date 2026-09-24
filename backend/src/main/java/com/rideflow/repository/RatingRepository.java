package com.rideflow.repository;

import com.rideflow.entity.Rating;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    boolean existsByRideIdAndRaterId(UUID rideId, UUID raterId);

    /** Served by {@code ix_ratings_ratee}. */
    @Query("select avg(r.score) as average, count(r) as count from Rating r where r.rateeId = :rateeId")
    RatingStats statsFor(@Param("rateeId") UUID rateeId);

    interface RatingStats {

        Double getAverage();

        long getCount();
    }
}
