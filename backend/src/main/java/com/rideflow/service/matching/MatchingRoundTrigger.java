package com.rideflow.service.matching;

import com.rideflow.service.ride.event.MatchingRoundRequestedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs a matching round as soon as the transaction that requested it commits, off the request thread.
 * If this is lost (crash between commit and execution), {@link MatchingSweeper} picks the ride up.
 */
@Component
public class MatchingRoundTrigger {

    private final DriverMatchingService matching;

    public MatchingRoundTrigger(DriverMatchingService matching) {
        this.matching = matching;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatchingRoundRequested(MatchingRoundRequestedEvent event) {
        matching.runNextRound(event.rideId());
    }
}
