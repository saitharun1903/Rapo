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

/** Append-only history of a ride's status changes (the ride timeline). */
@Entity
@Table(name = "ride_status_events")
public class RideStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ride_id", nullable = false, updatable = false)
    private UUID rideId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private RideStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private RideStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16, updatable = false)
    private ActorType actorType;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "reason", updatable = false)
    private String reason;

    @Column(name = "ride_version", nullable = false, updatable = false)
    private long rideVersion;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected RideStatusEvent() {
    }

    public RideStatusEvent(UUID rideId, RideStatus fromStatus, RideStatus toStatus, ActorType actorType,
                           UUID actorUserId, String reason, long rideVersion, Instant occurredAt) {
        this.rideId = rideId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorType = actorType;
        this.actorUserId = actorUserId;
        this.reason = reason;
        this.rideVersion = rideVersion;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getRideId() {
        return rideId;
    }

    public RideStatus getFromStatus() {
        return fromStatus;
    }

    public RideStatus getToStatus() {
        return toStatus;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getReason() {
        return reason;
    }

    public long getRideVersion() {
        return rideVersion;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof RideStatusEvent event && id != null && id.equals(event.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(RideStatusEvent.class);
    }
}
