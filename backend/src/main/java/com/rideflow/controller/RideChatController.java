package com.rideflow.controller;

import com.rideflow.dto.chat.RideMessageResponse;
import com.rideflow.dto.chat.SendMessageRequest;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.chat.RideChatService;
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

@RestController
@RequestMapping("/api/rides/{rideId}/messages")
@Tag(name = "Ride chat")
public class RideChatController {

    private final RideChatService chat;

    public RideChatController(RideChatService chat) {
        this.chat = chat;
    }

    @GetMapping
    @Operation(summary = "The conversation between the passenger and the assigned driver, oldest first")
    public List<RideMessageResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID rideId) {
        return chat.list(user, rideId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a message to the other participant; only while a driver is on the ride")
    public RideMessageResponse send(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID rideId,
                                    @Valid @RequestBody SendMessageRequest request) {
        return chat.send(user, rideId, request.body());
    }
}
