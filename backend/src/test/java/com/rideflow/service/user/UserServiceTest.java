package com.rideflow.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rideflow.dto.user.ChangePasswordRequest;
import com.rideflow.dto.user.UpdateProfileRequest;
import com.rideflow.entity.AuditAction;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.exception.DuplicateResourceException;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.ResourceNotFoundException;
import com.rideflow.exception.RideFlowException;
import com.rideflow.mapper.UserMapper;
import com.rideflow.repository.UserRepository;
import com.rideflow.service.audit.AuditService;
import com.rideflow.service.auth.RefreshTokenService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String PASSWORD = "Correct-horse-9";
    private static final String NEW_PASSWORD = "Battery-staple-7";
    private static final String PHONE = "+919876543210";

    @Mock
    private UserRepository users;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RefreshTokenService refreshTokens;
    @Mock
    private AuditService auditService;

    /** Cost 4: the lowest BCrypt allows, so the test stays fast while hashing for real. */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private UserService userService;
    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(users, userMapper, passwordEncoder, refreshTokens, auditService);
        user = User.register("asha@example.com", null, passwordEncoder.encode(PASSWORD), "Asha", Role.PASSENGER);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    @Test
    void updatingTheProfileTrimsTheNameAndTakesAFreePhoneNumber() {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.existsByPhoneAndIdNot(PHONE, user.getId())).thenReturn(false);

        userService.updateProfile(user.getId(), new UpdateProfileRequest("  Asha Rao ", " " + PHONE + " "));

        assertThat(user.getFullName()).isEqualTo("Asha Rao");
        assertThat(user.getPhone()).isEqualTo(PHONE);
        verify(userMapper).toResponse(user);
    }

    @Test
    void aBlankPhoneClearsItWithoutCheckingForDuplicates() {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        userService.updateProfile(user.getId(), new UpdateProfileRequest("Asha", "  "));

        assertThat(user.getPhone()).isNull();
        verify(users, never()).existsByPhoneAndIdNot(any(), any());
    }

    @Test
    void aPhoneNumberTakenByAnotherAccountIsRefusedAndNothingChanges() {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.existsByPhoneAndIdNot(PHONE, user.getId())).thenReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(user.getId(), new UpdateProfileRequest("Someone Else", PHONE)))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting("code").isEqualTo(ErrorCode.PHONE_TAKEN);
        assertThat(user.getFullName()).isEqualTo("Asha");
    }

    @Test
    void changingThePasswordNeedsTheCurrentOneAndSignsOutEverywhere() {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        userService.changePassword(user.getId(), new ChangePasswordRequest(PASSWORD, NEW_PASSWORD));

        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        verify(refreshTokens).revokeAllForUser(user.getId());
        verify(auditService).record(eq(user.getId()), eq(AuditAction.PASSWORD_CHANGED), eq("USER"), eq(user.getId()),
                anyMap());
    }

    @Test
    void aWrongCurrentPasswordChangesNothing() {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        String before = user.getPasswordHash();

        assertThatThrownBy(() -> userService.changePassword(user.getId(),
                new ChangePasswordRequest("Not-my-password-1", NEW_PASSWORD)))
                .isInstanceOf(RideFlowException.class)
                .extracting("code").isEqualTo(ErrorCode.WRONG_CURRENT_PASSWORD);
        assertThat(user.getPasswordHash()).isEqualTo(before);
        verifyNoInteractions(refreshTokens, auditService);
    }

    @Test
    void anUnknownUserIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(users.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(missing))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("code").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
