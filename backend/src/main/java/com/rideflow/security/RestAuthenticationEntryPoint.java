package com.rideflow.security;

import com.rideflow.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Returns 401 as an {@code ApiError}: INVALID_TOKEN for a rejected bearer token, UNAUTHENTICATED otherwise. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String BEARER_CHALLENGE = "Bearer";

    private final ApiErrorResponseWriter writer;

    public RestAuthenticationEntryPoint(ApiErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
        if (ex instanceof InvalidBearerTokenException) {
            writer.write(request, response, ErrorCode.INVALID_TOKEN, "Access token is invalid or expired");
        } else {
            writer.write(request, response, ErrorCode.UNAUTHENTICATED, "Authentication is required");
        }
    }
}
