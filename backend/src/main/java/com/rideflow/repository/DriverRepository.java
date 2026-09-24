package com.rideflow.repository;

import com.rideflow.entity.Driver;
import com.rideflow.entity.DriverVerificationStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    boolean existsByLicenseNumber(String licenseNumber);

    @EntityGraph(attributePaths = "user")
    Optional<Driver> findWithUserById(UUID id);

    @EntityGraph(attributePaths = "user")
    Page<Driver> findByVerificationStatus(DriverVerificationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Driver> findAllBy(Pageable pageable);

    /** Serialises availability changes made by background jobs against the driver's own actions. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Driver d where d.id = :id")
    Optional<Driver> findByIdForUpdate(@Param("id") UUID id);
}
