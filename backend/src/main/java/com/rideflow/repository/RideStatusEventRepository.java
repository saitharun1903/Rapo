package com.rideflow.repository;

import com.rideflow.entity.RideStatusEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideStatusEventRepository extends JpaRepository<RideStatusEvent, Long> {

    List<RideStatusEvent> findByRideIdOrderByIdAsc(UUID rideId);
}
