package com.rideflow.dto.user;

import com.rideflow.dto.auth.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Full replacement of the editable profile fields; a null phone removes it. */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Pattern(regexp = ValidationPatterns.E164_PHONE, message = "must be in E.164 format, e.g. +919876543210")
        @Nullable String phone) {
}
