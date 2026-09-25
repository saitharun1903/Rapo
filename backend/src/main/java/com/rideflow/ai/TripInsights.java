package com.rideflow.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The model's analysis of a completed trip, after validation. Limits are enforced here rather than in the
 * JSON schema, because providers' structured-output modes do not all support length constraints.
 *
 * @param comparison against the passenger's own history; {@code null} when there is not enough history
 * @param factKeysUsed the fact keys the text is based on (must be a subset of the supplied facts)
 */
public record TripInsights(
        @NotBlank @Size(max = 600) String summary,
        @NotBlank @Size(max = 800) String fareExplanation,
        @NotNull @Size(max = 5) List<@Valid @NotNull Observation> observations,
        @NotNull @Size(max = 3) List<@NotBlank @Size(max = 200) String> recommendations,
        @Size(max = 400) String comparison,
        @NotEmpty List<@NotBlank String> factKeysUsed) {

    public record Observation(@NotNull Type type, @NotBlank @Size(max = 300) String text) {

        public enum Type {
            FARE, ROUTE, TIME, COMPARISON, OTHER
        }
    }
}
