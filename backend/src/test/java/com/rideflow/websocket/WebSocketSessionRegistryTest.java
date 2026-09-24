package com.rideflow.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rideflow.config.RealtimeProperties;
import com.rideflow.entity.Role;
import com.rideflow.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

class WebSocketSessionRegistryTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-24T10:00:00Z"));
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final WebSocketSessionRegistry registry = new WebSocketSessionRegistry(new RealtimeProperties(
            Duration.ofSeconds(10), Duration.ofSeconds(1), CONNECT_TIMEOUT,
            new RealtimeProperties.Presence(Duration.ofMinutes(2), 100)), clock, meters);
    private final WebSocketHandler decorated = registry.decorate(mock(WebSocketHandler.class));

    private WebSocketSession open(String id) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        decorated.afterConnectionEstablished(session);
        return session;
    }

    private static void authenticate(WebSocketSession session, Role role, Instant tokenExpiresAt) {
        session.getAttributes().put(SessionAttributes.ROLE, role);
        session.getAttributes().put(SessionAttributes.TOKEN_EXPIRES_AT, tokenExpiresAt);
    }

    private double gauge(Role role) {
        return meters.get("rideflow.ws.sessions.active").tag("role", role.name()).gauge().value();
    }

    @Test
    void closesSessionsWhoseTokenExpiredWithTheReconnectCode() throws Exception {
        WebSocketSession expiring = open("a");
        authenticate(expiring, Role.PASSENGER, clock.instant().plus(Duration.ofMinutes(15)));
        WebSocketSession valid = open("b");
        authenticate(valid, Role.PASSENGER, clock.instant().plus(Duration.ofMinutes(30)));

        clock.advance(Duration.ofMinutes(15));

        assertThat(registry.closeExpiredSessions()).isEqualTo(1);
        verify(expiring).close(WebSocketSessionRegistry.TOKEN_EXPIRED);
        verify(valid, never()).close(org.mockito.ArgumentMatchers.any(CloseStatus.class));
        assertThat(gauge(Role.PASSENGER)).isEqualTo(1.0);
    }

    @Test
    void closesSocketsThatNeverAuthenticatedAfterTheConnectTimeout() throws Exception {
        WebSocketSession idle = open("idle");

        clock.advance(CONNECT_TIMEOUT.minusSeconds(1));
        assertThat(registry.closeExpiredSessions()).isZero();

        clock.advance(Duration.ofSeconds(1));
        assertThat(registry.closeExpiredSessions()).isEqualTo(1);
        verify(idle).close(WebSocketSessionRegistry.CONNECT_TIMEOUT);
    }

    @Test
    void countsOpenAuthenticatedSessionsPerRoleAndForgetsClosedOnes() throws Exception {
        WebSocketSession driver = open("d");
        authenticate(driver, Role.DRIVER, clock.instant().plus(Duration.ofMinutes(15)));
        open("unauthenticated");

        assertThat(gauge(Role.DRIVER)).isEqualTo(1.0);
        assertThat(gauge(Role.PASSENGER)).isZero();

        decorated.afterConnectionClosed(driver, CloseStatus.NORMAL);
        assertThat(gauge(Role.DRIVER)).isZero();
    }
}
