package com.rideflow.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TripQuestionRequest(@NotBlank @Size(max = 500) String question) {
}
