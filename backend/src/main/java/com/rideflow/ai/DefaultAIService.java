package com.rideflow.ai;

import com.rideflow.ai.LlmClient.LlmRequest;
import com.rideflow.ai.LlmClient.LlmResponse;
import com.rideflow.ai.PromptTemplates.Prompt;
import com.rideflow.ai.PromptTemplates.Rendered;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Renders the versioned prompt, calls the model and validates the answer. An invalid answer gets one corrective
 * retry that tells the model exactly what was wrong; a second invalid answer fails with INVALID_RESPONSE.
 *
 * <p>Metrics: {@code rideflow.ai.requests{operation, provider, outcome}} and
 * {@code rideflow.ai.latency{operation, provider}}.
 */
public class DefaultAIService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAIService.class);
    private static final String OPERATION_ANALYSIS = "analysis";
    private static final String OPERATION_QUESTION = "question";
    private static final String NO_HISTORY = "The passenger has too few earlier trips for a comparison: comparison must be null.";
    private static final String CORRECTION = """

            Your previous answer was rejected: %s
            Return a corrected JSON object only.""";
    /** Characters that would let a question close its data block or smuggle in markup. */
    private static final String QUESTION_DELIMITERS = "[<>]";

    private final LlmClient client;
    private final PromptTemplates prompts;
    private final AIResponseValidator validator;
    private final JsonMapper jsonMapper;
    private final MeterRegistry meters;

    public DefaultAIService(LlmClient client, PromptTemplates prompts, AIResponseValidator validator,
                            JsonMapper jsonMapper, MeterRegistry meters) {
        this.client = client;
        this.prompts = prompts;
        this.validator = validator;
        this.jsonMapper = jsonMapper;
        this.meters = meters;
    }

    @Override
    public AIResult<TripInsights> analyzeTrip(TripFacts facts) {
        Rendered prompt = prompts.render(Prompt.TRIP_ANALYSIS, Map.of(
                "facts", jsonMapper.writeValueAsString(facts.values()),
                "observations", bullets(facts),
                "history_note", facts.hasHistory() ? "" : NO_HISTORY));
        return call(OPERATION_ANALYSIS, prompt, AIOutputSchemas.TRIP_INSIGHTS,
                text -> validator.insights(text, facts));
    }

    @Override
    public AIResult<TripAnswer> answerQuestion(TripFacts facts, String question) {
        Rendered prompt = prompts.render(Prompt.TRIP_QUESTION, Map.of(
                "facts", jsonMapper.writeValueAsString(facts.values()),
                "observations", bullets(facts),
                "question", question.replaceAll(QUESTION_DELIMITERS, " ").strip()));
        return call(OPERATION_QUESTION, prompt, AIOutputSchemas.TRIP_ANSWER,
                text -> validator.answer(text, facts));
    }

    @Override
    public AIProviderInfo providerInfo() {
        return client.info();
    }

    private <T> AIResult<T> call(String operation, Rendered prompt, Map<String, Object> schema,
                                 Function<String, T> validate) {
        long start = System.nanoTime();
        long inputTokens = 0;
        long outputTokens = 0;
        int attempts = 0;
        String user = prompt.user();
        try {
            while (true) {
                attempts++;
                LlmResponse response = client.complete(new LlmRequest(prompt.system(), user, schema));
                inputTokens += response.inputTokens();
                outputTokens += response.outputTokens();
                try {
                    T value = validate.apply(response.text());
                    long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
                    record(operation, "success", start);
                    return new AIResult<>(value, client.info(), inputTokens, outputTokens, latencyMs, attempts);
                } catch (AIException invalid) {
                    if (attempts > 1) {
                        throw invalid;
                    }
                    log.info("AI {} answer rejected, asking for a correction: {}", operation, invalid.getMessage());
                    user = prompt.user() + CORRECTION.formatted(invalid.getMessage());
                }
            }
        } catch (AIException ex) {
            record(operation, ex.code().name().toLowerCase(Locale.ROOT), start);
            throw ex;
        }
    }

    private static String bullets(TripFacts facts) {
        if (facts.observations().isEmpty()) {
            return "(none)";
        }
        return facts.observations().stream().map(observation -> "- " + observation).collect(Collectors.joining("\n"));
    }

    private void record(String operation, String outcome, long start) {
        String provider = client.info().provider();
        Counter.builder("rideflow.ai.requests")
                .description("AI operations by outcome")
                .tag("operation", operation)
                .tag("provider", provider)
                .tag("outcome", outcome)
                .register(meters)
                .increment();
        Timer.builder("rideflow.ai.latency")
                .description("AI operation latency, including retries and corrections")
                .tag("operation", operation)
                .tag("provider", provider)
                .register(meters)
                .record(Duration.ofNanos(System.nanoTime() - start));
    }
}
