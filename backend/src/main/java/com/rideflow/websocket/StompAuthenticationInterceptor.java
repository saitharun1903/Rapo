package com.rideflow.websocket;

import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.security.RideFlowJwtAuthenticationConverter;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP {@code CONNECT} frame with the same access token as the REST API (browsers
 * cannot set headers on the WebSocket handshake, so the token travels in the frame). Later frames are
 * refused once that token has expired: a long-lived socket must not outlive the credential that opened it.
 * A rejected frame produces an ERROR frame and closes the connection.
 */
@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthenticationInterceptor.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final RideFlowJwtAuthenticationConverter authenticationConverter;
    private final Clock clock;

    public StompAuthenticationInterceptor(JwtDecoder jwtDecoder,
                                          RideFlowJwtAuthenticationConverter authenticationConverter, Clock clock) {
        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
        this.clock = clock;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            authenticate(accessor);
        } else if (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
            requireLiveSession(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new RideFlowException(ErrorCode.UNAUTHENTICATED,
                    "Send 'Authorization: Bearer <access token>' in the CONNECT frame");
        }
        Jwt jwt;
        Authentication authentication;
        try {
            jwt = jwtDecoder.decode(header.substring(BEARER_PREFIX.length()));
            authentication = authenticationConverter.convert(jwt);
        } catch (JwtException ex) {
            log.debug("STOMP CONNECT rejected: {}", ex.getMessage());
            throw new RideFlowException(ErrorCode.INVALID_TOKEN, "Access token is invalid or expired");
        }
        if (jwt.getExpiresAt() == null) {
            throw new RideFlowException(ErrorCode.INVALID_TOKEN, "Access token has no expiry");
        }
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        accessor.setUser(authentication);
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes != null) {
            attributes.put(SessionAttributes.ROLE, user.role());
            attributes.put(SessionAttributes.TOKEN_EXPIRES_AT, jwt.getExpiresAt());
        }
    }

    private void requireLiveSession(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null || accessor.getSessionAttributes() == null) {
            throw new RideFlowException(ErrorCode.UNAUTHENTICATED, "Send CONNECT with an access token first");
        }
        Instant expiresAt = SessionAttributes.tokenExpiresAt(accessor.getSessionAttributes()).orElse(Instant.MIN);
        if (!clock.instant().isBefore(expiresAt)) {
            throw new RideFlowException(ErrorCode.INVALID_TOKEN,
                    "Access token expired; reconnect with a fresh token");
        }
    }
}
