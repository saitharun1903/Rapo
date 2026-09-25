package com.rideflow.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Versioned prompt templates from {@code classpath:prompts/<name>/<version>/{system,user}.txt}. The version is
 * stored with every result, so a change in wording is traceable; a changed prompt gets a new version directory
 * rather than being edited in place. Templates are loaded once at startup and a missing one fails startup.
 */
@Component
public class PromptTemplates {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z_]+)}}");

    /** The prompts in use and their versions. */
    public enum Prompt {
        TRIP_ANALYSIS("trip-analysis", "v1"),
        TRIP_QUESTION("trip-question", "v1");

        private final String name;
        private final String version;

        Prompt(String name, String version) {
            this.name = name;
            this.version = version;
        }

        /** Stored as {@code prompt_version}, e.g. {@code trip-analysis/v1}. */
        public String id() {
            return name + "/" + version;
        }
    }

    /** A rendered prompt pair. */
    public record Rendered(String system, String user) {
    }

    private record Template(String system, String user) {
    }

    private final Map<Prompt, Template> templates = new EnumMap<>(Prompt.class);

    public PromptTemplates() {
        for (Prompt prompt : Prompt.values()) {
            templates.put(prompt, new Template(load(prompt, "system.txt"), load(prompt, "user.txt")));
        }
    }

    public Rendered render(Prompt prompt, Map<String, String> variables) {
        Template template = templates.get(prompt);
        return new Rendered(fill(template.system(), variables, prompt), fill(template.user(), variables, prompt));
    }

    private static String fill(String template, Map<String, String> variables, Prompt prompt) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = variables.get(matcher.group(1));
            if (value == null) {
                throw new IllegalArgumentException("No value for {{" + matcher.group(1) + "}} in " + prompt.id());
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String load(Prompt prompt, String file) {
        ClassPathResource resource = new ClassPathResource("prompts/" + prompt.id() + "/" + file);
        try {
            // A Windows checkout can turn the files' line endings into CRLF; the model gets the same text anywhere.
            return resource.getContentAsString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException ex) {
            throw new UncheckedIOException("Missing prompt template " + resource.getPath(), ex);
        }
    }
}
