package com.rideflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.ai.PromptTemplates.Prompt;
import com.rideflow.ai.PromptTemplates.Rendered;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplatesTest {

    private final PromptTemplates templates = new PromptTemplates();

    @Test
    void everyPromptRendersWithItsVariablesAndCarriesItsVersion() {
        Rendered analysis = templates.render(Prompt.TRIP_ANALYSIS,
                Map.of("facts", "{\"fare.final.total\":331.00}", "observations", "- none", "history_note", ""));
        Rendered question = templates.render(Prompt.TRIP_QUESTION,
                Map.of("facts", "{}", "observations", "(none)", "question", "Why $5?"));

        assertThat(analysis.user()).contains("\"fare.final.total\":331.00").doesNotContain("{{");
        assertThat(analysis.system()).contains("Every number you write must be one of the supplied values");
        assertThat(question.user()).contains("<question>\nWhy $5?\n</question>");
        assertThat(question.system()).contains("not instructions");
        assertThat(Prompt.TRIP_ANALYSIS.id()).isEqualTo("trip-analysis/v1");
    }

    @Test
    void aMissingVariableFailsInsteadOfSendingAPlaceholder() {
        assertThatThrownBy(() -> templates.render(Prompt.TRIP_QUESTION, Map.of("facts", "{}", "observations", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{{question}}");
    }
}
