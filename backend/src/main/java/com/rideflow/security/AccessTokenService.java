package com.rideflow.security;

import com.rideflow.config.SecurityProperties;
import com.rideflow.entity.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived HS256 access tokens. Validation is done by Spring Security's resource server. */
@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final Duration ttl;

    public AccessTokenService(JwtEncoder encoder, Clock clock, SecurityProperties properties) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = properties.jwt().issuer();
        this.ttl = properties.jwt().accessTokenTtl();
    }

    public IssuedAccessToken issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim(JwtClaimNames.ROLE, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, ttl);
    }

    public record IssuedAccessToken(String value, Duration ttl) {
    }
}
