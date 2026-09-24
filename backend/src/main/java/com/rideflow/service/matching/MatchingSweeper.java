package com.rideflow.service.matching;

import com.rideflow.config.MatchingProperties;
import com.rideflow.entity.RideStatus;
import com.rideflow.repository.RideRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Advances rides whose matching round timed out (offers expired or nobody was in range) and recovers
 * rides whose after-commit trigger never ran. State lives in the database, so this is restart-safe and
 * can run on every instance: {@link DriverMatchingService#runNextRound} serialises per ride.
 */
@Component
public class MatchingSweeper {

    private static final Logger log = LoggerFactory.getLogger(MatchingSweeper.class);

    private final RideRepository rides;
    private final DriverMatchingService matching;
    private final MatchingProperties properties;
    private final Clock clock;

    public MatchingSweeper(RideRepository rides, DriverMatchingService matching, MatchingProperties properties,
                           Clock clock) {
        this.rides = rides;
        this.matching = matching;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${rideflow.matching.sweep-interval}")
    public void sweep() {
        Instant now = clock.instant();
        List<UUID> due = rides.findRidesDueForNextRound(RideStatus.AWAITING_DRIVER,
                now.minus(properties.triggerGrace()), now.minus(properties.offerTtl()), now,
                PageRequest.ofSize(properties.sweepBatchSize()));
        for (UUID rideId : due) {
            try {
                matching.runNextRound(rideId);
            } catch (RuntimeException ex) {
                // Isolate failures per ride: one broken ride must not block matching for the others.
                log.error("Matching round failed for ride {}; will retry on the next sweep", rideId, ex);
            }
        }
    }
}
