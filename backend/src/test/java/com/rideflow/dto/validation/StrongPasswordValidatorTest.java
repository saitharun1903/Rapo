package com.rideflow.dto.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {"abcdefghi1", "S3cure-passphrase", "ünïcødé-pass-9"})
    void acceptsPasswordsMeetingPolicy(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "onlyletters", "1234567890", ""})
    void rejectsPasswordsViolatingPolicy(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void rejectsPasswordsLongerThanBcryptLimitInBytes() {
        // 36 two-byte characters = 72 bytes (allowed); 37 = 74 bytes (rejected).
        String atLimit = "é".repeat(35) + "a1";
        String overLimit = "é".repeat(36) + "a1";
        assertThat(validator.isValid(atLimit, null)).isTrue();
        assertThat(validator.isValid(overLimit, null)).isFalse();
    }

    @Test
    void treatsNullAsValidSoNotNullCanReportIt() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
