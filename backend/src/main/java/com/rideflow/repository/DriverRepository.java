package com.rideflow.repository;

import com.rideflow.entity.Driver;
import com.rideflow.entity.DriverVerificationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    boolean existsByLicenseNumber(String licenseNumber);

    @EntityGraph(attributePaths = "user")
    Optional<Driver> findWithUserById(UUID id);

    @EntityGraph(attributePaths = "user")
    Page<Driver> findByVerificationStatus(DriverVerificationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Driver> findAllBy(Pageable pageable);
}
