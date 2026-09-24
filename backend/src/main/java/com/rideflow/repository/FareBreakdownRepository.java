package com.rideflow.repository;

import com.rideflow.entity.FareBreakdown;
import com.rideflow.entity.FareKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareBreakdownRepository extends JpaRepository<FareBreakdown, UUID> {

    List<FareBreakdown> findByRideId(UUID rideId);

    Optional<FareBreakdown> findByRideIdAndKind(UUID rideId, FareKind kind);

    List<FareBreakdown> findByRideIdIn(Collection<UUID> rideIds);
}
