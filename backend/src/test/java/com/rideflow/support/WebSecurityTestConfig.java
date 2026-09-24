package com.rideflow.support;

import com.rideflow.config.JwtConfig;
import com.rideflow.config.SecurityConfig;
import com.rideflow.config.TimeConfig;
import com.rideflow.exception.ApiErrorFactory;
import com.rideflow.security.AccessTokenService;
import com.rideflow.security.ApiErrorResponseWriter;
import com.rideflow.security.RefreshTokenCookies;
import com.rideflow.security.RestAccessDeniedHandler;
import com.rideflow.security.RestAuthenticationEntryPoint;
import com.rideflow.security.RideFlowJwtAuthenticationConverter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * The real security stack for {@code @WebMvcTest} slices: filter chains, JWT encode/decode, error
 * writers. Tests authenticate with genuine signed tokens rather than mocked authentication.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({
    SecurityConfig.class,
    JwtConfig.class,
    TimeConfig.class,
    RideFlowJwtAuthenticationConverter.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    ApiErrorResponseWriter.class,
    ApiErrorFactory.class,
    RefreshTokenCookies.class,
    AccessTokenService.class
})
public class WebSecurityTestConfig {
}
