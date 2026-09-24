package com.rideflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.config.SecurityProperties;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AccessTokenServiceTest {

    private static final String SECRET = "unit-test-signing-secret-with-32-plus-bytes";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private static SecurityProperties properties(String issuer) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(SECRET, issuer, TTL),
                new SecurityProperties.RefreshToken(Duration.ofDays(14), Duration.ofDays(7), "rf_refresh", true, "Lax", "/api/auth"),
                4);
    }

    private AccessTokenService service(Clock clock) {
        return new AccessTokenService(
                NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build(), clock, properties("rideflow"));
    }

    private NimbusJwtDecoder decoder(String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    private static User user(Role role) {
        User user = User.register("a@example.com", null, "{bcrypt}hash", "A", role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void issuedTokenCarriesSubjectRoleIssuerAndExpiry() {
        User user = user(Role.DRIVER);

        AccessTokenService.IssuedAccessToken issued = service(Clock.systemUTC()).issue(user);
        Jwt jwt = decoder("rideflow").decode(issued.value());

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString(JwtClaimNames.ROLE)).isEqualTo("DRIVER");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(TTL);
        assertThat(issued.ttl()).isEqualTo(TTL);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String token = service(Clock.systemUTC()).issue(user(Role.PASSENGER)).value();

        assertThatThrownBy(() -> decoder("someone-else").decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        Clock anHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        String token = service(anHourAgo).issue(user(Role.PASSENGER)).value();

        assertThatThrownBy(() -> decoder("rideflow").decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = service(Clock.systemUTC()).issue(user(Role.PASSENGER)).value();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertThatThrownBy(() -> decoder("rideflow").decode(tampered)).isInstanceOf(JwtException.class);
    }
}
