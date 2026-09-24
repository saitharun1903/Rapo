package com.rideflow.repository;

import com.rideflow.entity.AuditAction;
import com.rideflow.entity.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityIdAndAction(UUID entityId, AuditAction action);
}
