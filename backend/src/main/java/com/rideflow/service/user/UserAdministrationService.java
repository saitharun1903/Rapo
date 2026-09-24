package com.rideflow.service.user;

import com.rideflow.dto.common.PageResponse;
import com.rideflow.dto.user.UserResponse;
import com.rideflow.entity.AuditAction;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.entity.UserStatus;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import com.rideflow.exception.ResourceNotFoundException;
import com.rideflow.mapper.UserMapper;
import com.rideflow.repository.UserRepository;
import com.rideflow.repository.UserSpecifications;
import com.rideflow.service.audit.AuditService;
import com.rideflow.service.auth.RefreshTokenService;
import com.rideflow.service.driver.DriverAvailabilityService;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdministrationService {

    private static final String ENTITY_TYPE = "USER";

    private final UserRepository users;
    private final UserMapper userMapper;
    private final RefreshTokenService refreshTokens;
    private final AuditService auditService;
    private final DriverAvailabilityService driverAvailability;

    public UserAdministrationService(UserRepository users, UserMapper userMapper, RefreshTokenService refreshTokens,
                                     AuditService auditService, DriverAvailabilityService driverAvailability) {
        this.users = users;
        this.userMapper = userMapper;
        this.refreshTokens = refreshTokens;
        this.auditService = auditService;
        this.driverAvailability = driverAvailability;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(Role role, UserStatus status, String text, Pageable pageable) {
        Specification<User> filter = Specification.allOf(
                UserSpecifications.hasRole(role),
                UserSpecifications.hasStatus(status),
                UserSpecifications.matchesText(text));
        return PageResponse.of(users.findAll(filter, pageable), userMapper::toResponse);
    }

    /**
     * Suspending signs the user out of every session and takes a driver offline (refused mid-trip). Access
     * tokens already issued remain valid until they expire (at most the access-token TTL), which is the
     * accepted trade-off of stateless JWTs.
     */
    @Transactional
    public UserResponse changeStatus(UUID adminId, UUID userId, UserStatus newStatus, String reason) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        if (user.getRole() == Role.ADMIN) {
            throw new InvalidStateException(ErrorCode.INVALID_USER_STATE,
                    "Admin accounts cannot be suspended or reactivated through the API");
        }
        if (user.getStatus() == newStatus) {
            return userMapper.toResponse(user);
        }
        user.changeStatus(newStatus);
        if (newStatus == UserStatus.SUSPENDED) {
            if (user.getRole() == Role.DRIVER) {
                driverAvailability.forceOffline(userId);
            }
            refreshTokens.revokeAllForUser(userId);
        }
        auditService.record(adminId, AuditAction.USER_STATUS_CHANGED, ENTITY_TYPE, userId,
                Map.of("status", newStatus.name(), "reason", reason.trim()));
        return userMapper.toResponse(user);
    }
}
