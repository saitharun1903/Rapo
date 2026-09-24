package com.rideflow.service.auth;

import com.rideflow.cache.RateLimitScope;
import com.rideflow.cache.RateLimiter;
import com.rideflow.dto.auth.AuthResponse;
import com.rideflow.dto.auth.LoginRequest;
import com.rideflow.dto.auth.RegisterRequest;
import com.rideflow.dto.user.UserResponse;
import com.rideflow.entity.AuditAction;
import com.rideflow.entity.User;
import com.rideflow.exception.AuthenticationFailedException;
import com.rideflow.exception.DuplicateResourceException;
import com.rideflow.exception.ErrorCode;
import com.rideflow.mapper.UserMapper;
import com.rideflow.repository.UserRepository;
import com.rideflow.security.AccessTokenService;
import com.rideflow.security.AccessTokenService.IssuedAccessToken;
import com.rideflow.security.OpaqueTokenGenerator;
import com.rideflow.service.audit.AuditService;
import com.rideflow.utility.TextNormalizer;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String ENTITY_TYPE = "USER";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Email or password is incorrect";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final AuditService auditService;
    private final UserMapper userMapper;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    /** Compared against when the email is unknown so response time does not reveal whether an account exists. */
    private final String timingEqualizationHash;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokens,
            RefreshTokenService refreshTokens,
            AuditService auditService,
            UserMapper userMapper,
            OpaqueTokenGenerator tokenGenerator,
            RateLimiter rateLimiter,
            Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.auditService = auditService;
        this.userMapper = userMapper;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.timingEqualizationHash = passwordEncoder.encode(tokenGenerator.generate());
    }

    /**
     * @param clientIp the caller's address, for the per-IP rate limit (behind a proxy, only correct if the proxy
     *                 overwrites {@code X-Forwarded-For}; see docs/architecture.md section 9)
     */
    @Transactional
    public UserResponse register(RegisterRequest request, String clientIp) {
        rateLimiter.acquire(RateLimitScope.REGISTER, clientIp);
        String email = TextNormalizer.email(request.email());
        String phone = TextNormalizer.trimToNull(request.phone());
        if (users.existsByEmail(email)) {
            throw new DuplicateResourceException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
        if (phone != null && users.existsByPhone(phone)) {
            throw new DuplicateResourceException(ErrorCode.PHONE_TAKEN, "An account with this phone number already exists");
        }
        User user = users.save(User.register(
                email, phone, passwordEncoder.encode(request.password()), request.fullName().trim(),
                request.accountType().role()));
        return userMapper.toResponse(user);
    }

    /** Rate-limited per IP and email before the password is checked, so guessing is slowed either way. */
    @Transactional
    public AuthSession login(LoginRequest request, String clientIp) {
        String email = TextNormalizer.email(request.email());
        rateLimiter.acquire(RateLimitScope.LOGIN, clientIp + "|" + email);
        Optional<User> candidate = users.findByEmail(email);
        if (candidate.isEmpty()) {
            passwordEncoder.matches(request.password(), timingEqualizationHash);
            throw new AuthenticationFailedException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE);
        }
        User user = candidate.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.recordIndependently(null, AuditAction.LOGIN_FAILED, ENTITY_TYPE, user.getId(), Map.of());
            throw new AuthenticationFailedException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE);
        }
        // Checked only after the password so suspension status is not revealed to someone guessing passwords.
        if (!user.isActive()) {
            throw new AuthenticationFailedException(ErrorCode.ACCOUNT_SUSPENDED, "Account is suspended");
        }
        user.recordLogin(clock.instant());
        return session(user, refreshTokens.issueNewFamily(user));
    }

    /** Rolls back nothing on auth failures: a detected token reuse must still commit the family revocation. */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public AuthSession refresh(String rawRefreshToken) {
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokens.rotate(rawRefreshToken);
        return session(rotated.user(), rotated.token());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revokeFamilyOf(rawRefreshToken);
    }

    private AuthSession session(User user, RefreshTokenService.IssuedRefreshToken refreshToken) {
        IssuedAccessToken accessToken = accessTokens.issue(user);
        AuthResponse response = new AuthResponse(
                accessToken.value(), AuthResponse.BEARER, accessToken.ttl().toSeconds(), userMapper.toResponse(user));
        return new AuthSession(response, refreshToken.rawToken());
    }
}
