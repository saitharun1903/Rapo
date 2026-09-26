package com.rideflow.service.chat;

import com.rideflow.config.ChatProperties;
import com.rideflow.repository.RideMessageRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes chat messages once their ride has been over for the retention period. Safe on every instance. */
@Component
public class RideMessageRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RideMessageRetentionJob.class);

    private final RideMessageRepository messages;
    private final ChatProperties properties;
    private final Clock clock;

    public RideMessageRetentionJob(RideMessageRepository messages, ChatProperties properties, Clock clock) {
        this.messages = messages;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${rideflow.chat.cleanup-cron}")
    public void purge() {
        int deleted = messages.deleteForRidesEndedBefore(clock.instant().minus(properties.retention()));
        log.info("Deleted {} chat messages of rides that ended more than {} ago", deleted, properties.retention());
    }
}
