package com.rideflow.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    static final int MIN_BYTES = 10;
    /** BCrypt only uses the first 72 bytes; longer input would silently weaken the check. */
    static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true;
        }
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        return bytes >= MIN_BYTES
                && bytes <= MAX_BYTES
                && password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit);
    }
}
