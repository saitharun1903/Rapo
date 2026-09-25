package com.rideflow.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * JSON schemas of the answers, sent to the provider to constrain the output (Ollama {@code format}, Anthropic
 * structured outputs). Only keywords both support are used: types, {@code enum}, {@code anyOf}, required
 * properties and {@code additionalProperties: false}. Lengths and counts are checked by
 * {@link AIResponseValidator} instead.
 */
public final class AIOutputSchemas {

    public static final Map<String, Object> TRIP_INSIGHTS = object(Map.of(
            "summary", Map.of("type", "string"),
            "fareExplanation", Map.of("type", "string"),
            "observations", array(object(Map.of(
                    "type", Map.of("type", "string", "enum", enumNames(TripInsights.Observation.Type.values())),
                    "text", Map.of("type", "string")))),
            "recommendations", array(Map.of("type", "string")),
            "comparison", Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "null"))),
            "factKeysUsed", array(Map.of("type", "string"))));

    public static final Map<String, Object> TRIP_ANSWER = object(Map.of(
            "answerable", Map.of("type", "boolean"),
            "answer", Map.of("type", "string"),
            "factKeysUsed", array(Map.of("type", "string"))));

    private AIOutputSchemas() {
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", properties.keySet().stream().sorted().toList(),
                "additionalProperties", false);
    }

    private static Map<String, Object> array(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    private static List<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
