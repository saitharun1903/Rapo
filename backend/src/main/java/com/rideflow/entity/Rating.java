package com.rideflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** One participant's rating of the other after a completed ride; each participant rates a ride at most once. */
@Entity
@Table(name = "ratings")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ride_id", nullable = false, updatable = false)
    private UUID rideId;

    @Column(name = "rater_id", nullable = false, updatable = false)
    private UUID raterId;

    @Column(name = "ratee_id", nullable = false, updatable = false)
    private UUID rateeId;

    @Column(name = "score", nullable = false, updatable = false)
    private short score;

    @Column(name = "comment", length = 500, updatable = false)
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Rating() {
    }

    public static Rating of(UUID rideId, UUID raterId, UUID rateeId, int score, String comment) {
        Rating rating = new Rating();
        rating.rideId = rideId;
        rating.raterId = raterId;
        rating.rateeId = rateeId;
        rating.score = (short) score;
        rating.comment = comment;
        return rating;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRideId() {
        return rideId;
    }

    public UUID getRaterId() {
        return raterId;
    }

    public UUID getRateeId() {
        return rateeId;
    }

    public int getScore() {
        return score;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Rating rating && id != null && id.equals(rating.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
