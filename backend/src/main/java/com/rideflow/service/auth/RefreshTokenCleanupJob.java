package com.rideflow.service.auth;

import com.rideflow.config.SecurityProperties;
import com.rideflow.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes refresh tokens that expired longer ago than the retention window. Expired rows are kept for a
 * while so reuse of a recently expired token can still be traced.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;
    private final Duration retention;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokens, Clock clock, SecurityProperties properties) {
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.retention = properties.refreshToken().cleanupRetention();
    }

    @Scheduled(cron = "${rideflow.security.refresh-token.cleanup-cron}")
    @Transactional
    public void purgeExpired() {
        int deleted = refreshTokens.deleteExpiredBefore(clock.instant().minus(retention));
        log.info("Purged {} refresh tokens expired more than {} ago", deleted, retention);
    }
}
