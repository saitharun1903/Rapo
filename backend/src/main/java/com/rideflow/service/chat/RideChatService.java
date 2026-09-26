package com.rideflow.service.chat;

import com.rideflow.cache.RateLimitScope;
import com.rideflow.cache.RateLimiter;
import com.rideflow.dto.chat.RideMessageResponse;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideMessage;
import com.rideflow.entity.RideStatus;
import com.rideflow.entity.Role;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import com.rideflow.repository.RideMessageRepository;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.chat.event.RideMessageSentEvent;
import com.rideflow.service.event.DomainEventPublisher;
import com.rideflow.service.ride.RideAccessPolicy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-ride chat between a ride's passenger and its assigned driver (docs/feature-spec.md section 3).
 *
 * <p>Only the two participants can read or write; everyone else, including a driver who was only offered the
 * ride, gets 404. Messages can be sent while a driver is engaged, from assignment until the trip ends. When a
 * ride is re-dispatched, the new driver sees only what was said since they accepted, never the conversation with
 * the driver before them.
 */
@Service
public class RideChatService {

    /** From the moment a driver is assigned until the trip ends. */
    static final Set<RideStatus> OPEN = EnumSet.of(RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVING,
            RideStatus.DRIVER_ARRIVED, RideStatus.IN_PROGRESS);

    private final RideAccessPolicy access;
    private final RideMessageRepository messages;
    private final DomainEventPublisher events;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final Counter passengerMessages;
    private final Counter driverMessages;

    public RideChatService(RideAccessPolicy access, RideMessageRepository messages, DomainEventPublisher events,
                           RateLimiter rateLimiter, Clock clock, MeterRegistry meters) {
        this.access = access;
        this.messages = messages;
        this.events = events;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.passengerMessages = counter(meters, Role.PASSENGER);
        this.driverMessages = counter(meters, Role.DRIVER);
    }

    @Transactional
    public RideMessageResponse send(AuthenticatedUser user, UUID rideId, String body) {
        rateLimiter.acquire(RateLimitScope.CHAT, user.id().toString());
        Ride ride = participantRide(user, rideId);
        if (!OPEN.contains(ride.getStatus())) {
            throw new InvalidStateException(ErrorCode.CHAT_CLOSED,
                    "Messages can be sent only while a driver is on this ride");
        }
        RideMessage message = messages.save(
                RideMessage.of(rideId, user.id(), user.role(), body.strip(), clock.instant()));
        events.publish(new RideMessageSentEvent(message.getId(), rideId, ride.getPassengerId(), ride.getDriverId()));
        (user.role() == Role.PASSENGER ? passengerMessages : driverMessages).increment();
        return toResponse(message);
    }

    @Transactional(readOnly = true)
    public List<RideMessageResponse> list(AuthenticatedUser user, UUID rideId) {
        Ride ride = participantRide(user, rideId);
        Instant since = user.role() == Role.DRIVER ? ride.getAcceptedAt() : null;
        return messages.findByRideIdOrderBySentAtAscIdAsc(rideId).stream()
                .filter(message -> since == null || !message.getSentAt().isBefore(since))
                .map(RideChatService::toResponse)
                .toList();
    }

    /** For the realtime push, whose recipients come from the event; no access check. */
    @Transactional(readOnly = true)
    public Optional<RideMessageResponse> findForPush(UUID messageId) {
        return messages.findById(messageId).map(RideChatService::toResponse);
    }

    private Ride participantRide(AuthenticatedUser user, UUID rideId) {
        Ride ride = access.loadVisible(user, rideId);
        boolean participant = switch (user.role()) {
            case PASSENGER -> ride.getPassengerId().equals(user.id());
            case DRIVER -> ride.isAssignedTo(user.id());
            case ADMIN -> false;
        };
        if (!participant) {
            throw RideAccessPolicy.notFound();
        }
        return ride;
    }

    private static RideMessageResponse toResponse(RideMessage message) {
        return new RideMessageResponse(message.getId(), message.getRideId(), message.getSenderId(),
                message.getSenderRole(), message.getBody(), message.getSentAt());
    }

    private static Counter counter(MeterRegistry meters, Role role) {
        return Counter.builder("rideflow.chat.messages")
                .description("In-ride chat messages sent, by the sender's role")
                .tag("role", role.name())
                .register(meters);
    }
}
