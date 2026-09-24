package com.rideflow.security;

import com.rideflow.config.SecurityProperties;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * HTTP transport of the refresh token: an HttpOnly cookie scoped to the auth endpoints. Because the
 * cookie is sent automatically by browsers, endpoints that read it also require a custom request
 * header, which cross-site forms cannot set (CSRF defence).
 */
@Component
public class RefreshTokenCookies {

    public static final String CSRF_HEADER = "X-Requested-With";
    public static final String CSRF_HEADER_VALUE = "rideflow";

    private final SecurityProperties.RefreshToken properties;

    public RefreshTokenCookies(SecurityProperties properties) {
        this.properties = properties.refreshToken();
    }

    public ResponseCookie issue(String rawToken) {
        return build(rawToken, properties.ttl());
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, properties.cookieName());
        return Optional.ofNullable(cookie).map(Cookie::getValue).filter(value -> !value.isBlank());
    }

    public void requireCsrfHeader(HttpServletRequest request) {
        if (!CSRF_HEADER_VALUE.equals(request.getHeader(CSRF_HEADER))) {
            throw new RideFlowException(ErrorCode.CSRF_HEADER_MISSING,
                    "Header " + CSRF_HEADER + ": " + CSRF_HEADER_VALUE + " is required");
        }
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path(properties.cookiePath())
                .maxAge(maxAge)
                .build();
    }
}
