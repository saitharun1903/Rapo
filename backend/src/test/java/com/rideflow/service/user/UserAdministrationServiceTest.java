package com.rideflow.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rideflow.entity.AuditAction;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.entity.UserStatus;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import com.rideflow.exception.ResourceNotFoundException;
import com.rideflow.mapper.UserMapper;
import com.rideflow.repository.UserRepository;
import com.rideflow.service.audit.AuditService;
import com.rideflow.service.auth.RefreshTokenService;
import com.rideflow.service.driver.DriverAvailabilityService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserAdministrationServiceTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String REASON = "Repeated no-shows";

    @Mock
    private UserRepository users;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RefreshTokenService refreshTokens;
    @Mock
    private AuditService auditService;
    @Mock
    private DriverAvailabilityService driverAvailability;

    private UserAdministrationService service;

    @BeforeEach
    void setUp() {
        service = new UserAdministrationService(users, userMapper, refreshTokens, auditService, driverAvailability);
    }

    private User existing(Role role, UserStatus status) {
        User user = User.register(role.name().toLowerCase() + "@example.com", null, "hash", "Someone", role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.changeStatus(status);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void suspendingADriverTakesThemOfflineSignsThemOutAndRecordsWhy() {
        User driver = existing(Role.DRIVER, UserStatus.ACTIVE);

        service.changeStatus(ADMIN_ID, driver.getId(), UserStatus.SUSPENDED, "  " + REASON + " ");

        assertThat(driver.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(driverAvailability).forceOffline(driver.getId());
        verify(refreshTokens).revokeAllForUser(driver.getId());
        verify(auditService).record(ADMIN_ID, AuditAction.USER_STATUS_CHANGED, "USER", driver.getId(),
                Map.of("status", "SUSPENDED", "reason", REASON));
    }

    @Test
    void suspendingAPassengerSignsThemOutButHasNoDriverStateToChange() {
        User passenger = existing(Role.PASSENGER, UserStatus.ACTIVE);

        service.changeStatus(ADMIN_ID, passenger.getId(), UserStatus.SUSPENDED, REASON);

        verify(refreshTokens).revokeAllForUser(passenger.getId());
        verifyNoInteractions(driverAvailability);
    }

    @Test
    void reactivatingRestoresAccessWithoutRevokingAnything() {
        User passenger = existing(Role.PASSENGER, UserStatus.SUSPENDED);

        service.changeStatus(ADMIN_ID, passenger.getId(), UserStatus.ACTIVE, REASON);

        assertThat(passenger.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(refreshTokens, never()).revokeAllForUser(passenger.getId());
        verify(auditService).record(eq(ADMIN_ID), eq(AuditAction.USER_STATUS_CHANGED), eq("USER"),
                eq(passenger.getId()), eq(Map.of("status", "ACTIVE", "reason", REASON)));
    }

    @Test
    void settingTheStatusAUserAlreadyHasChangesAndRecordsNothing() {
        User passenger = existing(Role.PASSENGER, UserStatus.SUSPENDED);

        service.changeStatus(ADMIN_ID, passenger.getId(), UserStatus.SUSPENDED, REASON);

        verify(userMapper).toResponse(passenger);
        verifyNoInteractions(refreshTokens, auditService, driverAvailability);
    }

    @Test
    void adminAccountsCannotBeSuspendedThroughTheApi() {
        User admin = existing(Role.ADMIN, UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.changeStatus(ADMIN_ID, admin.getId(), UserStatus.SUSPENDED, REASON))
                .isInstanceOf(InvalidStateException.class)
                .extracting("code").isEqualTo(ErrorCode.INVALID_USER_STATE);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verifyNoInteractions(refreshTokens, auditService);
    }

    @Test
    void anUnknownUserIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(users.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(ADMIN_ID, missing, UserStatus.SUSPENDED, REASON))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("code").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
