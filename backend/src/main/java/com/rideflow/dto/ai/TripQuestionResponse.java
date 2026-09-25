package com.rideflow.dto.ai;

import com.rideflow.ai.AIFailureCode;
import com.rideflow.entity.TripQuestionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A question about a trip and its answer. For a question that could not be answered, {@code answerable},
 * {@code answer} and {@code factKeysUsed} are {@code null} and {@code failureCode} says why.
 */
public record TripQuestionResponse(
        UUID id,
        String question,
        TripQuestionStatus status,
        AIFailureCode failureCode,
        Boolean answerable,
        String answer,
        List<String> factKeysUsed,
        Instant askedAt) {
}
