package com.rideflow.dto.chat;

import com.rideflow.entity.Role;
import java.time.Instant;
import java.util.UUID;

/** One chat message. {@code senderRole} is PASSENGER or DRIVER; clients use it to tell sides apart. */
public record RideMessageResponse(UUID id, UUID rideId, UUID senderId, Role senderRole, String body, Instant sentAt) {
}
