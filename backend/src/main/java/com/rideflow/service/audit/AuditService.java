package com.rideflow.service.audit;

import com.rideflow.entity.AuditAction;
import com.rideflow.entity.AuditLog;
import com.rideflow.repository.AuditLogRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogs;

    public AuditService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    /** Records the action as part of the caller's transaction: if the action rolls back, so does the entry. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, AuditAction action, String entityType, UUID entityId, Map<String, Object> details) {
        auditLogs.save(new AuditLog(actorId, action, entityType, entityId, details));
    }

    /** Records the action even if the caller's transaction rolls back (e.g. failed login attempts). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(
            UUID actorId, AuditAction action, String entityType, UUID entityId, Map<String, Object> details) {
        auditLogs.save(new AuditLog(actorId, action, entityType, entityId, details));
    }
}
