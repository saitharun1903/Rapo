package com.rideflow.service.auth;

import com.rideflow.dto.validation.StrongPasswordValidator;
import com.rideflow.entity.AuditAction;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.repository.UserRepository;
import com.rideflow.service.audit.AuditService;
import com.rideflow.utility.TextNormalizer;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the first ADMIN account from {@code BOOTSTRAP_ADMIN_EMAIL}/{@code BOOTSTRAP_ADMIN_PASSWORD} when
 * no admin exists yet. Admins cannot self-register, so this is how a fresh deployment gets one. Does
 * nothing when the email is unset or an admin already exists.
 */
@Component
public class AdminBootstrapper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapper.class);
    private static final String ENTITY_TYPE = "USER";
    private static final String DEFAULT_ADMIN_NAME = "Platform Admin";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final String email;
    private final String password;

    public AdminBootstrapper(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            @Value("${rideflow.bootstrap.admin.email:}") String email,
            @Value("${rideflow.bootstrap.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email) || users.existsByRole(Role.ADMIN)) {
            return;
        }
        if (!StringUtils.hasText(password) || !new StrongPasswordValidator().isValid(password, null)) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must be 10-72 bytes with at least one letter and one digit");
        }
        User admin = users.save(User.register(
                TextNormalizer.email(email), null, passwordEncoder.encode(password), DEFAULT_ADMIN_NAME, Role.ADMIN));
        auditService.record(null, AuditAction.ADMIN_BOOTSTRAPPED, ENTITY_TYPE, admin.getId(), Map.of());
        log.info("Bootstrapped initial admin account {}", admin.getId());
    }
}
