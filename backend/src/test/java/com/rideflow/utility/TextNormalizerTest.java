package com.rideflow.utility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void normalisesEmailCaseAndWhitespace() {
        assertThat(TextNormalizer.email("  Asha.Rao@Example.COM ")).isEqualTo("asha.rao@example.com");
    }

    @Test
    void normalisesIdentifiersLikePlates() {
        assertThat(TextNormalizer.identifier("ts 09 ab-1234")).isEqualTo("TS09AB1234");
    }

    @Test
    void trimsBlankToNull() {
        assertThat(TextNormalizer.trimToNull("   ")).isNull();
        assertThat(TextNormalizer.trimToNull(null)).isNull();
        assertThat(TextNormalizer.trimToNull(" +919876543210 ")).isEqualTo("+919876543210");
    }
}
