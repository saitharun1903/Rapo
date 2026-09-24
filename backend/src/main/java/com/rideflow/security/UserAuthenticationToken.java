package com.rideflow.security;

import java.util.Collection;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

/** Authentication whose principal is an {@link AuthenticatedUser}, usable via {@code @AuthenticationPrincipal}. */
public final class UserAuthenticationToken extends AbstractOAuth2TokenAuthenticationToken<Jwt> {

    private final AuthenticatedUser user;

    public UserAuthenticationToken(Jwt jwt, AuthenticatedUser user, Collection<? extends GrantedAuthority> authorities) {
        super(jwt, user, jwt, authorities);
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public Map<String, Object> getTokenAttributes() {
        return getToken().getClaims();
    }

    @Override
    public String getName() {
        return user.id().toString();
    }
}
