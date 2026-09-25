package com.rideflow.ai;

import com.rideflow.config.AIProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks model output before anyone sees it (docs/architecture.md section 12.3):
 * <ol>
 *   <li>it is one JSON object of the expected shape;</li>
 *   <li>lengths and counts are within limits;</li>
 *   <li>every cited fact key exists;</li>
 *   <li>a history comparison appears only when history facts were supplied;</li>
 *   <li><b>numeric grounding</b>: every number in the text is one of the supplied values, allowing rounding and
 *       a small relative tolerance. This is what stops a model from inventing prices or distances.</li>
 * </ol>
 * Problems are reported together, so a single corrective retry can fix all of them.
 */
@Component
public class AIResponseValidator {

    /** Numbers as written in prose: "245", "1,245.50", "13.1", "1.2". */
    private static final Pattern NUMBER = Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?");
    private static final int MAX_REPORTED_PROBLEMS = 10;

    private final JsonMapper mapper;
    private final Validator validator;
    private final double tolerance;

    public AIResponseValidator(JsonMapper jsonMapper, Validator validator, AIProperties properties) {
        this.mapper = jsonMapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        this.validator = validator;
        this.tolerance = properties.validation().numericTolerance();
    }

    public TripInsights insights(String text, TripFacts facts) {
        TripInsights insights = parse(text, TripInsights.class);
        List<String> problems = constraintProblems(insights);
        if (problems.isEmpty()) {
            problems.addAll(unknownKeys(insights.factKeysUsed(), facts));
            if (insights.comparison() != null && !facts.hasHistory()) {
                problems.add("comparison must be null: no history facts were supplied");
            }
            problems.addAll(ungrounded(Stream.concat(
                    Stream.of(insights.summary(), insights.fareExplanation(), insights.comparison()),
                    Stream.concat(insights.observations().stream().map(TripInsights.Observation::text),
                            insights.recommendations().stream())).toList(), facts));
        }
        return requireValid(insights, problems);
    }

    public TripAnswer answer(String text, TripFacts facts) {
        TripAnswer answer = parse(text, TripAnswer.class);
        List<String> problems = constraintProblems(answer);
        if (problems.isEmpty()) {
            problems.addAll(unknownKeys(answer.factKeysUsed(), facts));
            problems.addAll(ungrounded(List.of(answer.answer()), facts));
        }
        return requireValid(answer, problems);
    }

    private <T> T parse(String text, Class<T> type) {
        String json = extractObject(text);
        try {
            T value = mapper.readValue(json, type);
            if (value == null) {
                throw invalid("The answer was empty; return one JSON object");
            }
            return value;
        } catch (JacksonException ex) {
            throw invalid("The answer is not valid JSON of the required shape: " + ex.getOriginalMessage());
        }
    }

    /** Tolerates a code fence or a sentence around the object, which some local models add despite instructions. */
    private static String extractObject(String text) {
        if (text == null) {
            throw invalid("The answer was empty; return one JSON object");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw invalid("The answer contains no JSON object");
        }
        return text.substring(start, end + 1);
    }

    private <T> List<String> constraintProblems(T value) {
        List<String> problems = new ArrayList<>();
        validator.validate(value).stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(AIResponseValidator::describe)
                .forEach(problems::add);
        return problems;
    }

    private static String describe(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + " " + violation.getMessage();
    }

    private static List<String> unknownKeys(List<String> keys, TripFacts facts) {
        return keys.stream()
                .filter(key -> !facts.values().containsKey(key))
                .map(key -> "factKeysUsed contains \"" + key + "\", which is not one of the supplied fact keys")
                .toList();
    }

    private List<String> ungrounded(List<String> texts, TripFacts facts) {
        List<BigDecimal> known = knownNumbers(facts);
        Set<String> problems = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher matcher = NUMBER.matcher(text);
            while (matcher.find()) {
                BigDecimal number = new BigDecimal(matcher.group().replace(",", ""));
                if (known.stream().noneMatch(value -> matches(number, value))) {
                    problems.add("the number " + matcher.group() + " is not one of the supplied facts");
                }
            }
        }
        return new ArrayList<>(problems);
    }

    private static List<BigDecimal> knownNumbers(TripFacts facts) {
        List<BigDecimal> known = new ArrayList<>();
        facts.values().values().stream()
                .filter(Number.class::isInstance)
                // Prose states magnitudes ("12% lower"), so signs of the facts do not matter.
                .map(value -> new BigDecimal(value.toString()).abs())
                .forEach(known::add);
        for (String observation : facts.observations()) {
            Matcher matcher = NUMBER.matcher(observation);
            while (matcher.find()) {
                known.add(new BigDecimal(matcher.group().replace(",", "")));
            }
        }
        return known;
    }

    /** Equal within the tolerance, or equal to the value rounded to 0 or 1 decimal places. */
    private boolean matches(BigDecimal number, BigDecimal value) {
        BigDecimal allowed = value.abs().multiply(BigDecimal.valueOf(tolerance));
        return number.subtract(value).abs().compareTo(allowed) <= 0
                || number.compareTo(value.setScale(0, RoundingMode.HALF_UP)) == 0
                || number.compareTo(value.setScale(1, RoundingMode.HALF_UP)) == 0;
    }

    private static <T> T requireValid(T value, List<String> problems) {
        if (!problems.isEmpty()) {
            throw invalid(String.join("; ", problems.stream().limit(MAX_REPORTED_PROBLEMS).toList()));
        }
        return value;
    }

    private static AIException invalid(String problems) {
        return new AIException(AIFailureCode.INVALID_RESPONSE, problems);
    }
}
