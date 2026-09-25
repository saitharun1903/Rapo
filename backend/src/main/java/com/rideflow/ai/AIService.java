package com.rideflow.ai;

/**
 * Provider-independent AI operations on trip facts (docs/architecture.md section 12.2). Implementations never
 * see the database: callers assemble {@link TripFacts}, and every returned value has been validated against
 * them. Failures surface as {@link AIException}.
 */
public interface AIService {

    AIResult<TripInsights> analyzeTrip(TripFacts facts);

    AIResult<TripAnswer> answerQuestion(TripFacts facts, String question);

    AIProviderInfo providerInfo();
}
