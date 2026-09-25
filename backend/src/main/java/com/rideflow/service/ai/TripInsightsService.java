package com.rideflow.service.ai;

import com.rideflow.ai.AIException;
import com.rideflow.ai.AIFailureCode;
import com.rideflow.ai.AIProviderInfo;
import com.rideflow.ai.AIResult;
import com.rideflow.ai.AIService;
import com.rideflow.ai.PromptTemplates.Prompt;
import com.rideflow.ai.TripAnswer;
import com.rideflow.ai.TripFacts;
import com.rideflow.ai.TripInsights;
import com.rideflow.cache.RateLimitScope;
import com.rideflow.cache.RateLimiter;
import com.rideflow.config.AIProperties;
import com.rideflow.dto.ai.TripAnalysisResponse;
import com.rideflow.dto.ai.TripObservationResponse;
import com.rideflow.dto.ai.TripQuestionResponse;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import com.rideflow.entity.Role;
import com.rideflow.entity.TripAnalysisStatus;
import com.rideflow.entity.TripQuestionStatus;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import com.rideflow.exception.RideFlowException;
import com.rideflow.repository.RideRepository;
import com.rideflow.repository.TripAnalysisRepository;
import com.rideflow.repository.TripAnalysisRepository.TripAnalysisRow;
import com.rideflow.repository.TripQuestionRepository;
import com.rideflow.repository.TripQuestionRepository.NewQuestion;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.ai.TripFactsAssembler.AssembledTrip;
import com.rideflow.service.event.ProcessedEvents;
import com.rideflow.service.ride.RideAccessPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI Trip Intelligence for passengers (docs/architecture.md section 12): the analysis made after every
 * completed ride, its regeneration, and questions about a trip.
 *
 * <p>Only the ride's passenger may see or ask: the facts include their own trip history. The AI call never
 * runs inside a database transaction, and the ride lifecycle never waits for it: analysis starts from
 * {@code ride.completed} in its own consumer group.
 */
@Service
public class TripInsightsService {

    /** Idempotency key of the trip-analysis consumer ({@code processed_events.consumer}). */
    private static final String CONSUMER = "trip-analysis";
    private static final TypeReference<List<TripObservation>> OBSERVATION_LIST = new TypeReference<>() {
    };

    private final RideRepository rides;
    private final RideAccessPolicy access;
    private final TripFactsAssembler assembler;
    private final TripAnalysisRepository analyses;
    private final TripQuestionRepository questions;
    private final TripAnalysisRunner runner;
    private final AIService ai;
    private final ProcessedEvents processedEvents;
    private final RateLimiter rateLimiter;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final AIProperties properties;
    private final Clock clock;

    public TripInsightsService(RideRepository rides, RideAccessPolicy access, TripFactsAssembler assembler,
                               TripAnalysisRepository analyses, TripQuestionRepository questions,
                               TripAnalysisRunner runner, AIService ai, ProcessedEvents processedEvents,
                               RateLimiter rateLimiter, TransactionTemplate tx, JsonMapper json,
                               AIProperties properties, Clock clock) {
        this.rides = rides;
        this.access = access;
        this.assembler = assembler;
        this.analyses = analyses;
        this.questions = questions;
        this.runner = runner;
        this.ai = ai;
        this.processedEvents = processedEvents;
        this.rateLimiter = rateLimiter;
        this.tx = tx;
        this.json = json;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Kafka entry point ({@code ride.completed}). Records the facts as a PENDING analysis in one transaction,
     * then runs the model outside it. A redelivered event, or a ride that already has an analysis, is skipped.
     */
    public void analyzeCompletedRide(UUID eventId, UUID rideId) {
        Optional<TripFacts> facts = tx.execute(status -> startAnalysis(eventId, rideId));
        if (facts != null) {
            facts.ifPresent(pending -> runner.run(rideId, pending));
        }
    }

    public TripAnalysisResponse analysis(AuthenticatedUser user, UUID rideId) {
        Ride ride = completedRideOfPassenger(user, rideId);
        return analyses.findByRideId(rideId).map(this::toResponse).orElseGet(() -> {
            // Completed moments ago: the consumer has not stored the analysis yet. The observations need no AI.
            AssembledTrip trip = assembler.assemble(ride);
            return new TripAnalysisResponse(rideId, TripAnalysisStatus.PENDING, null, toResponses(trip.observations()),
                    null, null, null, Prompt.TRIP_ANALYSIS.id(), null);
        });
    }

    /**
     * Runs the analysis again in the background if it failed or was abandoned (still PENDING after
     * {@code rideflow.ai.pending-timeout}); answers 202 with the PENDING analysis.
     */
    public TripAnalysisResponse regenerate(AuthenticatedUser user, UUID rideId) {
        Ride ride = completedRideOfPassenger(user, rideId);
        rateLimiter.acquire(RateLimitScope.AI_REGENERATE, user.id().toString());
        Instant now = clock.instant();
        TripFacts facts = analyses.findByRideId(rideId)
                .map(row -> claimForRetry(row, now))
                .orElseGet(() -> createPending(ride, now));
        runner.runInBackground(rideId, facts);
        return analysis(user, rideId);
    }

    public TripQuestionResponse ask(AuthenticatedUser user, UUID rideId, String question) {
        Ride ride = completedRideOfPassenger(user, rideId);
        rateLimiter.acquire(RateLimitScope.AI_QUESTION, user.id().toString());
        TripFacts facts = analyses.findByRideId(rideId)
                .map(row -> json.readValue(row.factsJson(), TripFacts.class))
                .orElseGet(() -> assembler.assemble(ride).facts());
        UUID id = UUID.randomUUID();
        Instant askedAt = clock.instant();
        String text = question.strip();
        try {
            AIResult<TripAnswer> result = ai.answerQuestion(facts, text);
            questions.insert(new NewQuestion(id, rideId, user.id(), text, TripQuestionStatus.COMPLETED, null,
                    json.writeValueAsString(result.value()), result.provider().provider(), result.provider().model(),
                    Prompt.TRIP_QUESTION.id(), Math.toIntExact(result.latencyMs()), askedAt));
            TripAnswer answer = result.value();
            return new TripQuestionResponse(id, text, TripQuestionStatus.COMPLETED, null, answer.answerable(),
                    answer.answer(), answer.factKeysUsed(), askedAt);
        } catch (AIException ex) {
            AIProviderInfo provider = ai.providerInfo();
            TripQuestionStatus status = ex.code() == AIFailureCode.UNAVAILABLE
                    ? TripQuestionStatus.UNAVAILABLE : TripQuestionStatus.FAILED;
            questions.insert(new NewQuestion(id, rideId, user.id(), text, status, ex.code().name(), null,
                    provider.provider(), provider.model(), Prompt.TRIP_QUESTION.id(), null, askedAt));
            throw new RideFlowException(ErrorCode.AI_UNAVAILABLE, unavailableMessage(ex.code()));
        }
    }

    public List<TripQuestionResponse> questions(AuthenticatedUser user, UUID rideId) {
        completedRideOfPassenger(user, rideId);
        return questions.list(rideId, user.id()).stream().map(row -> {
            TripAnswer answer = row.answerJson() == null ? null : json.readValue(row.answerJson(), TripAnswer.class);
            return new TripQuestionResponse(row.id(), row.question(), row.status(),
                    row.failureCode() == null ? null : AIFailureCode.valueOf(row.failureCode()),
                    answer == null ? null : answer.answerable(),
                    answer == null ? null : answer.answer(),
                    answer == null ? null : answer.factKeysUsed(),
                    row.createdAt());
        }).toList();
    }

    private Optional<TripFacts> startAnalysis(UUID eventId, UUID rideId) {
        if (!processedEvents.firstDelivery(CONSUMER, eventId)) {
            return Optional.empty();
        }
        Ride ride = rides.findById(rideId).orElse(null);
        if (ride == null || ride.getStatus() != RideStatus.COMPLETED || analyses.findByRideId(rideId).isPresent()) {
            return Optional.empty();
        }
        AssembledTrip trip = assembler.assemble(ride);
        boolean created = analyses.insertPending(rideId, Prompt.TRIP_ANALYSIS.id(),
                json.writeValueAsString(trip.facts()), json.writeValueAsString(trip.observations()), clock.instant());
        return created ? Optional.of(trip.facts()) : Optional.empty();
    }

    private TripFacts claimForRetry(TripAnalysisRow row, Instant now) {
        if (!analyses.claimForRetry(row.rideId(), now.minus(properties.pendingTimeout()), now)) {
            throw new InvalidStateException(ErrorCode.AI_ANALYSIS_NOT_REGENERABLE,
                    "The analysis is " + row.status() + "; only a failed or abandoned analysis can be regenerated");
        }
        return json.readValue(row.factsJson(), TripFacts.class);
    }

    private TripFacts createPending(Ride ride, Instant now) {
        AssembledTrip trip = assembler.assemble(ride);
        if (!analyses.insertPending(ride.getId(), Prompt.TRIP_ANALYSIS.id(), json.writeValueAsString(trip.facts()),
                json.writeValueAsString(trip.observations()), now)) {
            throw new InvalidStateException(ErrorCode.AI_ANALYSIS_NOT_REGENERABLE, "The analysis is already being created");
        }
        return trip.facts();
    }

    /** Only the passenger: other participants must not see the passenger's history-based facts. */
    private Ride completedRideOfPassenger(AuthenticatedUser user, UUID rideId) {
        Ride ride = access.loadVisible(user, rideId);
        if (user.role() != Role.PASSENGER || !ride.getPassengerId().equals(user.id())) {
            throw RideAccessPolicy.notFound();
        }
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new InvalidStateException(ErrorCode.RIDE_NOT_COMPLETED, "Trip insights are available once the ride is completed");
        }
        return ride;
    }

    private TripAnalysisResponse toResponse(TripAnalysisRow row) {
        List<TripObservation> observations = json.readValue(row.observationsJson(), OBSERVATION_LIST);
        TripInsights insights = row.resultJson() == null ? null : json.readValue(row.resultJson(), TripInsights.class);
        return new TripAnalysisResponse(row.rideId(), row.status(),
                row.failureCode() == null ? null : AIFailureCode.valueOf(row.failureCode()),
                toResponses(observations), insights, row.provider(), row.model(), row.promptVersion(), row.updatedAt());
    }

    private static List<TripObservationResponse> toResponses(List<TripObservation> observations) {
        return observations.stream().map(o -> new TripObservationResponse(o.key(), o.text())).toList();
    }

    /** What the client may know about a failure; details stay in the logs. */
    private static String unavailableMessage(AIFailureCode code) {
        return switch (code) {
            case UNAVAILABLE, BUSY -> "Trip questions are not available right now; please try again later";
            case TIMEOUT -> "The answer took too long; please try again";
            case RATE_LIMITED -> "Too many questions are being answered right now; please try again shortly";
            case PROVIDER_ERROR, INVALID_RESPONSE, REFUSED -> "No reliable answer could be produced for this question";
        };
    }
}
