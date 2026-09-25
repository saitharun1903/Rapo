package com.rideflow.service.ai;

import com.rideflow.ai.AIException;
import com.rideflow.ai.AIFailureCode;
import com.rideflow.ai.AIProviderInfo;
import com.rideflow.ai.AIResult;
import com.rideflow.ai.AIService;
import com.rideflow.ai.TripFacts;
import com.rideflow.ai.TripInsights;
import com.rideflow.entity.TripAnalysisStatus;
import com.rideflow.repository.TripAnalysisRepository;
import com.rideflow.repository.TripAnalysisRepository.Outcome;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the AI analysis of a PENDING trip and records the outcome. It holds no database transaction while the
 * model works, which can take seconds (external) or a minute (a local model on CPU); the outcome is written
 * with one statement afterwards. AI failures are recorded, never thrown: the ride is complete whatever the AI
 * does.
 */
@Component
public class TripAnalysisRunner {

    private static final Logger log = LoggerFactory.getLogger(TripAnalysisRunner.class);

    private final AIService ai;
    private final TripAnalysisRepository analyses;
    private final JsonMapper json;
    private final Clock clock;

    public TripAnalysisRunner(AIService ai, TripAnalysisRepository analyses, JsonMapper json, Clock clock) {
        this.ai = ai;
        this.analyses = analyses;
        this.json = json;
        this.clock = clock;
    }

    public void run(UUID rideId, TripFacts facts) {
        long start = System.nanoTime();
        try {
            AIResult<TripInsights> result = ai.analyzeTrip(facts);
            analyses.markCompleted(rideId, outcome(result.provider(), result.latencyMs()),
                    json.writeValueAsString(result.value()), result.inputTokens(), result.outputTokens(), clock.instant());
            log.info("Trip analysis for ride {} completed by {} ({} ms, {} model calls)", rideId,
                    result.provider().provider(), result.latencyMs(), result.attempts());
        } catch (AIException ex) {
            TripAnalysisStatus status = ex.code() == AIFailureCode.UNAVAILABLE
                    ? TripAnalysisStatus.UNAVAILABLE : TripAnalysisStatus.FAILED;
            analyses.markFailed(rideId, status, ex.code().name(),
                    outcome(ai.providerInfo(), Duration.ofNanos(System.nanoTime() - start).toMillis()), clock.instant());
            log.warn("Trip analysis for ride {} {}: {} ({})", rideId, status, ex.code(), ex.getMessage());
        }
    }

    /** For regeneration requested over HTTP: the request returns 202 while the model works. */
    @Async
    public void runInBackground(UUID rideId, TripFacts facts) {
        run(rideId, facts);
    }

    private static Outcome outcome(AIProviderInfo provider, long latencyMs) {
        return new Outcome(provider.provider(), provider.model(), latencyMs);
    }
}
