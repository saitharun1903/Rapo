package com.rideflow.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.rideflow.config.SecurityProperties;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import com.rideflow.security.AccessTokenService;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.security.RideFlowJwtAuthenticationConverter;
import com.rideflow.support.MutableClock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class StompAuthenticationInterceptorTest {

    private static final String SECRET = "unit-test-signing-secret-with-32-plus-bytes";
    private static final String ISSUER = "rideflow";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final MutableClock clock = new MutableClock(Instant.now());
    private final SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final MessageChannel channel = mock(MessageChannel.class);
    private final StompAuthenticationInterceptor interceptor =
            new StompAuthenticationInterceptor(decoder(), new RideFlowJwtAuthenticationConverter(), clock);

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    private String token(Role role, String secret) {
        SecurityProperties properties = new SecurityProperties(new SecurityProperties.Jwt(secret, ISSUER, TTL),
                new SecurityProperties.RefreshToken(Duration.ofDays(14), Duration.ofDays(7), "rf_refresh", true, "Lax",
                        "/api/auth"), 4);
        SecretKeySpec signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        AccessTokenService tokens = new AccessTokenService(
                NimbusJwtEncoder.withSecretKey(signingKey).algorithm(MacAlgorithm.HS256).build(), clock, properties);
        User user = User.register("u@example.com", null, "{bcrypt}hash", "U", role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return tokens.issue(user).value();
    }

    private static StompHeaderAccessor frame(StompCommand command, Map<String, Object> session) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionAttributes(session);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private StompHeaderAccessor connect(String authorization, Map<String, Object> session) {
        StompHeaderAccessor accessor = frame(StompCommand.CONNECT, session);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        interceptor.preSend(message(accessor), channel);
        return accessor;
    }

    @Test
    void validTokenAuthenticatesTheSessionAndRecordsRoleAndExpiry() {
        Map<String, Object> session = new HashMap<>();

        StompHeaderAccessor accessor = connect("Bearer " + token(Role.DRIVER, SECRET), session);

        Authentication user = (Authentication) accessor.getUser();
        assertThat(user).isNotNull();
        assertThat(((AuthenticatedUser) user.getPrincipal()).role()).isEqualTo(Role.DRIVER);
        assertThat(session).containsEntry(SessionAttributes.ROLE, Role.DRIVER);
        assertThat(SessionAttributes.tokenExpiresAt(session)).isPresent();
    }

    @Test
    void connectWithoutBearerTokenIsRejected() {
        assertThatThrownBy(() -> connect(null, new HashMap<>()))
                .isInstanceOf(RideFlowException.class)
                .extracting(ex -> ((RideFlowException) ex).code()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThatThrownBy(() -> connect("Basic abc", new HashMap<>()))
                .extracting(ex -> ((RideFlowException) ex).code()).isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void forgedOrMalformedTokensAreRejected() {
        String forged = token(Role.ADMIN, "another-secret-that-is-also-32-bytes-long");

        assertThatThrownBy(() -> connect("Bearer " + forged, new HashMap<>()))
                .extracting(ex -> ((RideFlowException) ex).code()).isEqualTo(ErrorCode.INVALID_TOKEN);
        assertThatThrownBy(() -> connect("Bearer not.a.jwt", new HashMap<>()))
                .extracting(ex -> ((RideFlowException) ex).code()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void framesBeforeConnectAreRejected() {
        StompHeaderAccessor subscribe = frame(StompCommand.SUBSCRIBE, new HashMap<>());
        subscribe.setDestination("/user/queue/rides");

        assertThatThrownBy(() -> interceptor.preSend(message(subscribe), channel))
                .extracting(ex -> ((RideFlowException) ex).code()).isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void framesAfterTheTokenExpiresAreRejected() {
        Map<String, Object> session = new HashMap<>();
        StompHeaderAccessor connected = connect("Bearer " + token(Role.DRIVER, SECRET), session);

        StompHeaderAccessor send = frame(StompCommand.SEND, session);
        send.setUser(connected.getUser());
        send.setDestination(StompDestinations.DRIVER_LOCATION);
        interceptor.preSend(message(send), channel);

        clock.advance(TTL);
        assertThatThrownBy(() -> interceptor.preSend(message(send), channel))
                .extracting(ex -> ((RideFlowException) ex).code()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }
}
