package com.rideflow.service.user;

import com.rideflow.dto.user.ChangePasswordRequest;
import com.rideflow.dto.user.UpdateProfileRequest;
import com.rideflow.dto.user.UserResponse;
import com.rideflow.entity.AuditAction;
import com.rideflow.entity.User;
import com.rideflow.exception.DuplicateResourceException;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.ResourceNotFoundException;
import com.rideflow.exception.RideFlowException;
import com.rideflow.mapper.UserMapper;
import com.rideflow.repository.UserRepository;
import com.rideflow.service.audit.AuditService;
import com.rideflow.service.auth.RefreshTokenService;
import com.rideflow.utility.TextNormalizer;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final String ENTITY_TYPE = "USER";

    private final UserRepository users;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final AuditService auditService;

    public UserService(
            UserRepository users,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokens,
            AuditService auditService) {
        this.users = users;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return userMapper.toResponse(load(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = load(userId);
        String phone = TextNormalizer.trimToNull(request.phone());
        if (phone != null && users.existsByPhoneAndIdNot(phone, userId)) {
            throw new DuplicateResourceException(ErrorCode.PHONE_TAKEN, "An account with this phone number already exists");
        }
        user.updateProfile(request.fullName().trim(), phone);
        return userMapper.toResponse(user);
    }

    /** Changing the password signs the user out everywhere by revoking all refresh tokens. */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = load(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new RideFlowException(ErrorCode.WRONG_CURRENT_PASSWORD, "Current password is incorrect");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeAllForUser(userId);
        auditService.record(userId, AuditAction.PASSWORD_CHANGED, ENTITY_TYPE, userId, Map.of());
    }

    private User load(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
    }
}
