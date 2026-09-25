package com.rideflow.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The model's answer to a passenger's question about a trip.
 *
 * @param answerable {@code false} when the facts do not answer the question or it is off-topic; the answer
 *                   then says so instead of guessing
 */
public record TripAnswer(
        boolean answerable,
        @NotBlank @Size(max = 800) String answer,
        @NotNull List<@NotBlank String> factKeysUsed) {
}
