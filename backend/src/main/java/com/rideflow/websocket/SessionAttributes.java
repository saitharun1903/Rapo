package com.rideflow.websocket;

import com.rideflow.entity.Role;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Typed access to per-connection state. STOMP session attributes and the WebSocket session attributes are
 * the same map, so values written while handling a frame are visible to {@link WebSocketSessionRegistry}.
 */
final class SessionAttributes {

    static final String OPENED_AT = "rideflow.openedAt";
    static final String ROLE = "rideflow.role";
    static final String TOKEN_EXPIRES_AT = "rideflow.tokenExpiresAt";
    static final String LAST_LOCATION_AT = "rideflow.lastLocationAt";

    private SessionAttributes() {
    }

    static Optional<Role> role(Map<String, Object> attributes) {
        return Optional.ofNullable((Role) attributes.get(ROLE));
    }

    static Optional<Instant> tokenExpiresAt(Map<String, Object> attributes) {
        return Optional.ofNullable((Instant) attributes.get(TOKEN_EXPIRES_AT));
    }
}
