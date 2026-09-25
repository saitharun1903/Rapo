package com.rideflow.ai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the model may say about a trip: flat, keyed values taken from the database (for example
 * {@code fare.final.total}), plus the deterministic observations already computed from them. The model must
 * cite the keys it used, and every number it writes must be one of these values.
 *
 * @param values       fact key to number, text or boolean; iteration order is the order shown to the model
 * @param observations computed statements shown to the user regardless of AI (their numbers count as facts)
 */
public record TripFacts(Map<String, Object> values, List<String> observations) {

    /** Prefix of the passenger-history facts; present only when there is enough history to compare. */
    public static final String HISTORY_PREFIX = "history.";

    public TripFacts {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        observations = List.copyOf(observations);
    }

    public boolean hasHistory() {
        return values.keySet().stream().anyMatch(key -> key.startsWith(HISTORY_PREFIX));
    }
}
