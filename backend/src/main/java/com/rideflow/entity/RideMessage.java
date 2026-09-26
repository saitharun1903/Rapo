package com.rideflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A message between a ride's passenger and its assigned driver. Messages are never edited. */
@Entity
@Table(name = "ride_messages")
public class RideMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ride_id", nullable = false, updatable = false)
    private UUID rideId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, updatable = false, length = 20)
    private Role senderRole;

    @Column(name = "body", nullable = false, updatable = false, length = 500)
    private String body;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected RideMessage() {
    }

    public static RideMessage of(UUID rideId, UUID senderId, Role senderRole, String body, Instant sentAt) {
        if (senderRole != Role.PASSENGER && senderRole != Role.DRIVER) {
            throw new IllegalArgumentException("Only ride participants send messages, not " + senderRole);
        }
        RideMessage message = new RideMessage();
        message.rideId = rideId;
        message.senderId = senderId;
        message.senderRole = senderRole;
        message.body = body;
        message.sentAt = sentAt;
        return message;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRideId() {
        return rideId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public Role getSenderRole() {
        return senderRole;
    }

    public String getBody() {
        return body;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RideMessage message && id != null && id.equals(message.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
