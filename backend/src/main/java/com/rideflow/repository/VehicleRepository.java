package com.rideflow.repository;

import com.rideflow.entity.Vehicle;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    boolean existsByPlateNumber(String plateNumber);

    Optional<Vehicle> findByDriverIdAndActiveTrue(UUID driverId);

    List<Vehicle> findByDriverIdInAndActiveTrue(Collection<UUID> driverIds);
}
