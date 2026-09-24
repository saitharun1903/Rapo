package com.rideflow.websocket;

import com.rideflow.config.RealtimeProperties;
import com.rideflow.entity.Role;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

/**
 * Tracks open WebSocket connections on this instance so the server can close the ones it should not keep:
 * <ul>
 *   <li>authenticated sessions whose access token has expired. Without this, a socket that only receives
 *       pushes would keep streaming (for example a driver's live position) long after the token, or the
 *       account, stopped being valid. Clients reconnect with a fresh token (close code 4001).</li>
 *   <li>sockets that never sent an authenticated CONNECT within the connect timeout.</li>
 * </ul>
 * Also exposes {@code rideflow.ws.sessions.active{role}}.
 */
@Component
public class WebSocketSessionRegistry implements WebSocketHandlerDecoratorFactory {

    /** Private-use close code (4000-4999): the client should refresh its token and reconnect. */
    public static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4001, "Access token expired");
    public static final CloseStatus CONNECT_TIMEOUT = CloseStatus.POLICY_VIOLATION.withReason("No CONNECT received");

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final RealtimeProperties properties;
    private final Clock clock;

    public WebSocketSessionRegistry(RealtimeProperties properties, Clock clock, MeterRegistry meters) {
        this.properties = properties;
        this.clock = clock;
        for (Role role : Role.values()) {
            Gauge.builder("rideflow.ws.sessions.active", this, registry -> registry.countSessions(role))
                    .description("Open authenticated WebSocket sessions on this instance")
                    .tag("role", role.name())
                    .register(meters);
        }
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                session.getAttributes().put(SessionAttributes.OPENED_AT, clock.instant());
                sessions.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    /** Closes sessions whose token expired or that never authenticated. Returns how many were closed. */
    @Scheduled(fixedDelayString = "${rideflow.realtime.session-sweep-interval}")
    public int closeExpiredSessions() {
        Instant now = clock.instant();
        int closed = 0;
        for (WebSocketSession session : sessions.values()) {
            CloseStatus reason = closeReason(session.getAttributes(), now);
            if (reason != null && close(session, reason)) {
                closed++;
            }
        }
        return closed;
    }

    int countSessions(Role role) {
        return (int) sessions.values().stream()
                .filter(session -> SessionAttributes.role(session.getAttributes()).orElse(null) == role)
                .count();
    }

    private CloseStatus closeReason(Map<String, Object> attributes, Instant now) {
        Instant expiresAt = SessionAttributes.tokenExpiresAt(attributes).orElse(null);
        if (expiresAt != null) {
            return now.isBefore(expiresAt) ? null : TOKEN_EXPIRED;
        }
        Instant openedAt = (Instant) attributes.get(SessionAttributes.OPENED_AT);
        boolean connectOverdue = openedAt != null && !now.isBefore(openedAt.plus(properties.connectTimeout()));
        return connectOverdue ? CONNECT_TIMEOUT : null;
    }

    private boolean close(WebSocketSession session, CloseStatus reason) {
        sessions.remove(session.getId());
        try {
            session.close(reason);
            log.debug("Closed WebSocket session {}: {}", session.getId(), reason.getReason());
            return true;
        } catch (IOException ex) {
            // The socket is already broken; the container will clean it up. Nothing else to release here.
            log.debug("WebSocket session {} could not be closed cleanly: {}", session.getId(), ex.getMessage());
            return false;
        }
    }
}
