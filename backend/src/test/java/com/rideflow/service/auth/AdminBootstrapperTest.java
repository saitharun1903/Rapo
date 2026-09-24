package com.rideflow.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.repository.UserRepository;
import com.rideflow.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapperTest {

    @Mock
    private UserRepository users;
    @Mock
    private AuditService auditService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private AdminBootstrapper bootstrapper(String email, String password) {
        return new AdminBootstrapper(users, encoder, auditService, email, password);
    }

    @Test
    void doesNothingWhenEmailNotConfigured() {
        bootstrapper("", "").run(null);

        verifyNoInteractions(users, auditService);
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(true);

        bootstrapper("admin@example.com", "Strong-admin-1").run(null);

        verify(users, never()).save(any());
    }

    @Test
    void refusesWeakPassword() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> bootstrapper("admin@example.com", "admin").run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }

    @Test
    void createsAdminWithHashedPassword() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bootstrapper(" Admin@Example.com ", "Strong-admin-1").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(encoder.matches("Strong-admin-1", saved.getValue().getPasswordHash())).isTrue();
    }
}
