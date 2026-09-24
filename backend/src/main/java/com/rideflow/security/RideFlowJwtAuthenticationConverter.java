package com.rideflow.security;

import com.rideflow.entity.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Maps a validated access token to a {@link UserAuthenticationToken} with a single role authority. */
@Component
public class RideFlowJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AuthenticatedUser user = new AuthenticatedUser(parseSubject(jwt), parseRole(jwt));
        return new UserAuthenticationToken(jwt, user,
                List.of(new SimpleGrantedAuthority(user.role().authority())));
    }

    private static UUID parseSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BadJwtException("Access token subject is not a valid user id", ex);
        }
    }

    private static Role parseRole(Jwt jwt) {
        String role = jwt.getClaimAsString(JwtClaimNames.ROLE);
        if (role == null) {
            throw new BadJwtException("Access token has no role claim");
        }
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException ex) {
            throw new BadJwtException("Access token has an unknown role claim", ex);
        }
    }
}
