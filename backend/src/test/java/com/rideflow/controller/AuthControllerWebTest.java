package com.rideflow.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rideflow.dto.auth.AuthResponse;
import com.rideflow.dto.auth.LoginRequest;
import com.rideflow.dto.user.UserResponse;
import com.rideflow.entity.Role;
import com.rideflow.entity.UserStatus;
import com.rideflow.exception.AuthenticationFailedException;
import com.rideflow.exception.ErrorCode;
import com.rideflow.service.auth.AuthService;
import com.rideflow.service.auth.AuthSession;
import com.rideflow.support.WebSecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class AuthControllerWebTest {

    private static final UserResponse USER = new UserResponse(
            UUID.randomUUID(), "asha@example.com", null, "Asha", Role.PASSENGER, UserStatus.ACTIVE, Instant.now());

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerReturnsFieldErrorsInStandardShape() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","fullName":"","accountType":"PASSENGER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.traceId", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors[*].field", allOf(
                        hasItem("email"), hasItem("password"), hasItem("fullName"))));
        verifyNoInteractions(authService);
    }

    @Test
    void registerCannotCreateAdminAccounts() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"x@example.com","password":"Valid-pass-123","fullName":"X","accountType":"ADMIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(authService);
    }

    @Test
    void registerReturns201() throws Exception {
        when(authService.register(any(), anyString())).thenReturn(USER);

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"asha@example.com","password":"Valid-pass-123","fullName":"Asha","accountType":"PASSENGER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void malformedJsonIsRejectedWithoutLeakingParserDetails() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request is malformed or has invalid parameter types"));
    }

    @Test
    void loginReturnsAccessTokenAndSetsHttpOnlyRefreshCookie() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(new AuthSession(
                new AuthResponse("access.jwt", "Bearer", 900, USER), "raw-refresh"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"asha@example.com\",\"password\":\"Valid-pass-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.jwt"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("rf_refresh=raw-refresh"),
                        containsString("HttpOnly"),
                        containsString("Path=/api/auth"),
                        containsString("SameSite=Lax"))));
    }

    @Test
    void loginFailureIsGeneric401() throws Exception {
        when(authService.login(any(), anyString())).thenThrow(
                new AuthenticationFailedException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"asha@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"));
    }

    @Test
    void refreshRequiresCsrfHeader() throws Exception {
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("rf_refresh", "raw")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_HEADER_MISSING"));
        verifyNoInteractions(authService);
    }

    @Test
    void refreshWithoutCookieMeansNoSession() throws Exception {
        // Every page load asks; a visitor who never signed in is not an error.
        mvc.perform(post("/api/auth/refresh").header("X-Requested-With", "rideflow"))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        verifyNoInteractions(authService);
    }

    @Test
    void refreshRotatesCookie() throws Exception {
        when(authService.refresh("old-refresh")).thenReturn(new AuthSession(
                new AuthResponse("new.jwt", "Bearer", 900, USER), "new-refresh"));

        mvc.perform(post("/api/auth/refresh")
                        .header("X-Requested-With", "rideflow")
                        .cookie(new Cookie("rf_refresh", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.jwt"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("rf_refresh=new-refresh")));
    }

    @Test
    void logoutRevokesAndClearsCookie() throws Exception {
        mvc.perform(post("/api/auth/logout")
                        .header("X-Requested-With", "rideflow")
                        .cookie(new Cookie("rf_refresh", "current")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("rf_refresh="), containsString("Max-Age=0"))));
        verify(authService).logout("current");
    }
}
