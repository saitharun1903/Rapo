package com.rideflow.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.rideflow.config.AIProperties;
import java.io.InterruptedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The external provider: Claude through the official Anthropic Java SDK. The answer schema goes in
 * {@code output_config.format} (structured outputs), so the response is valid JSON of that shape; effort is
 * {@code low} by default because the answers are short. The SDK's own retries are switched off
 * ({@code maxRetries(0)} when the client is built) so that {@link ResilientLlmClient} is the only retry layer.
 *
 * <p>Server-side refusal fallbacks are enabled ({@code fallbacks: "default"}): if the model declines, the API
 * retries on a fallback model instead of returning a refusal. A refusal that still comes back is reported as
 * {@link AIFailureCode#REFUSED}.
 */
public class AnthropicLlmClient implements LlmClient {

    static final String PROVIDER = "external";
    private static final String BETA_HEADER = "anthropic-beta";
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    private static final String RETRY_AFTER = "retry-after";

    private final AnthropicClient client;
    private final AIProperties.External settings;

    public AnthropicLlmClient(AnthropicClient client, AIProperties.External settings) {
        this.client = client;
        this.settings = settings;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(settings.model())
                .maxTokens(settings.maxOutputTokens())
                .system(request.system())
                .addUserMessage(request.user())
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(settings.effort().toLowerCase(Locale.ROOT)))
                        .format(JsonOutputFormat.builder().schema(schema(request.jsonSchema())).build())
                        .build())
                .putAdditionalHeader(BETA_HEADER, FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();
        Message message;
        try {
            message = client.messages().create(params);
        } catch (RateLimitException ex) {
            throw new AIException(AIFailureCode.RATE_LIMITED, "Anthropic API rate limit", false, retryAfter(ex), ex);
        } catch (AnthropicServiceException ex) {
            // 5xx (including 529 overloaded) may succeed later; other 4xx are request or account problems.
            boolean serverSide = ex.statusCode() >= 500;
            throw new AIException(AIFailureCode.PROVIDER_ERROR, "Anthropic API returned " + ex.statusCode()
                    + ex.errorType().map(type -> " (" + type + ")").orElse(""), serverSide, null, ex);
        } catch (AnthropicException ex) {
            // OkHttp reports an expired call timeout as an InterruptedIOException, wrapped at varying depths.
            if (hasCause(ex, InterruptedIOException.class)) {
                throw new AIException(AIFailureCode.TIMEOUT, "Anthropic API did not answer within "
                        + settings.timeout(), false, null, ex);
            }
            boolean network = ex instanceof AnthropicIoException;
            throw new AIException(AIFailureCode.PROVIDER_ERROR, network ? "Anthropic API unreachable"
                    : "Anthropic API call failed: " + ex.getClass().getSimpleName(), network, null, ex);
        }
        return toResponse(message);
    }

    @Override
    public AIProviderInfo info() {
        return new AIProviderInfo(PROVIDER, settings.model());
    }

    private static LlmResponse toResponse(Message message) {
        StopReason stopReason = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stopReason)) {
            throw new AIException(AIFailureCode.REFUSED, "The model declined to answer");
        }
        if (StopReason.MAX_TOKENS.equals(stopReason)) {
            throw new AIException(AIFailureCode.INVALID_RESPONSE, "The answer was cut off at the output token limit");
        }
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        return new LlmResponse(text, message.usage().inputTokens(), message.usage().outputTokens());
    }

    private static JsonOutputFormat.Schema schema(Map<String, Object> jsonSchema) {
        JsonOutputFormat.Schema.Builder builder = JsonOutputFormat.Schema.builder();
        jsonSchema.forEach((key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)));
        return builder.build();
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    /** Retry-After is in seconds (possibly fractional); absent or unparsable means "not known". */
    private static Duration retryAfter(RateLimitException ex) {
        return ex.headers().values(RETRY_AFTER).stream().findFirst().map(value -> {
            try {
                return Duration.ofMillis(new BigDecimal(value.trim()).movePointRight(3).longValue());
            } catch (NumberFormatException unparsable) {
                return null;
            }
        }).orElse(null);
    }
}
