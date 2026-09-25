package com.rideflow.controller;

import com.rideflow.dto.ai.TripAnalysisResponse;
import com.rideflow.dto.ai.TripQuestionRequest;
import com.rideflow.dto.ai.TripQuestionResponse;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.ai.TripInsightsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** AI Trip Intelligence for the passenger of a completed ride (docs/api.md). */
@RestController
@RequestMapping("/api/trips/{rideId}/ai-analysis")
@Tag(name = "Trip insights")
public class TripInsightsController {

    private final TripInsightsService insights;

    public TripInsightsController(TripInsightsService insights) {
        this.insights = insights;
    }

    @GetMapping
    @Operation(summary = "Computed observations, plus the AI analysis once it is ready (status PENDING until then)")
    public TripAnalysisResponse analysis(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID rideId) {
        return insights.analysis(user, rideId);
    }

    @PostMapping("/regenerate")
    @Operation(summary = "Run a FAILED, UNAVAILABLE or abandoned analysis again (asynchronously)")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TripAnalysisResponse regenerate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID rideId) {
        return insights.regenerate(user, rideId);
    }

    @PostMapping("/questions")
    @Operation(summary = "Ask about this trip; answered from the trip's recorded facts only (503 AI_UNAVAILABLE if AI is down)")
    public TripQuestionResponse ask(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID rideId,
                                    @Valid @RequestBody TripQuestionRequest request) {
        return insights.ask(user, rideId, request.question());
    }

    @GetMapping("/questions")
    @Operation(summary = "Questions asked about this trip, oldest first")
    public List<TripQuestionResponse> questions(@AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable UUID rideId) {
        return insights.questions(user, rideId);
    }
}
