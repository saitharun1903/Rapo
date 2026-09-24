package com.rideflow.repository;

import com.rideflow.entity.FareBreakdown;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareBreakdownRepository extends JpaRepository<FareBreakdown, UUID> {

    List<FareBreakdown> findByRideId(UUID rideId);

    List<FareBreakdown> findByRideIdIn(Collection<UUID> rideIds);
}
