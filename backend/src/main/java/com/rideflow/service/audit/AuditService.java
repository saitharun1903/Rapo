package com.rideflow.service.audit;

import com.rideflow.dto.admin.AuditLogResponse;
import com.rideflow.dto.common.PageResponse;
import com.rideflow.entity.AuditAction;
import com.rideflow.entity.AuditLog;
import com.rideflow.repository.AuditLogRepository;
import com.rideflow.repository.AuditLogSpecifications;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(AuditAction action, String entityType, Instant from, Instant to,
                                                 Pageable pageable) {
        Specification<AuditLog> filter = Specification.allOf(
                AuditLogSpecifications.hasAction(action),
                AuditLogSpecifications.hasEntityType(entityType),
                AuditLogSpecifications.createdFrom(from),
                AuditLogSpecifications.createdBefore(to));
        return PageResponse.of(auditLogs.findAll(filter, pageable), log -> new AuditLogResponse(log.getId(),
                log.getActorUserId(), log.getAction(), log.getEntityType(), log.getEntityId(), log.getDetails(),
                log.getCreatedAt()));
    }
}
