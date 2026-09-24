package com.rideflow.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rideflow.dto.common.PageResponse;
import com.rideflow.dto.user.UserResponse;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.entity.UserStatus;
import com.rideflow.security.AccessTokenService;
import com.rideflow.service.driver.DriverAdministrationService;
import com.rideflow.service.driver.DriverOnboardingService;
import com.rideflow.service.user.UserAdministrationService;
import com.rideflow.service.user.UserService;
import com.rideflow.support.TestUsers;
import com.rideflow.support.WebSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Role rules, 401/403 shapes and security headers, exercised with real signed JWTs. */
@WebMvcTest({UserController.class, DriverProfileController.class, AdminDriverController.class, AdminUserController.class})
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class EndpointSecurityWebTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AccessTokenService accessTokens;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private DriverOnboardingService onboardingService;
    @MockitoBean
    private DriverAdministrationService driverAdministrationService;
    @MockitoBean
    private UserAdministrationService userAdministrationService;

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.issue(user).value());
    }

    @Test
    void missingTokenIs401WithBearerChallenge() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/api/users/me"));
    }

    @Test
    void invalidTokenIs401InvalidToken() throws Exception {
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void authenticatedUserResolvesToOwnProfile() throws Exception {
        User passenger = TestUsers.withRole(Role.PASSENGER);
        when(userService.getProfile(passenger.getId())).thenReturn(new UserResponse(
                passenger.getId(), passenger.getEmail(), null, passenger.getFullName(), Role.PASSENGER,
                UserStatus.ACTIVE, Instant.now()));

        mvc.perform(as(passenger, get("/api/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(passenger.getId().toString()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")));
    }

    @Test
    void passengerCannotUseDriverEndpoints() throws Exception {
        mvc.perform(as(TestUsers.withRole(Role.PASSENGER), get("/api/drivers/me")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(onboardingService);
    }

    @Test
    void driverCannotUseAdminEndpoints() throws Exception {
        mvc.perform(as(TestUsers.withRole(Role.DRIVER), get("/api/admin/users")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(userAdministrationService);
    }

    @Test
    void adminCanListDriversWithAllowListedSort() throws Exception {
        when(driverAdministrationService.list(isNull(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, "createdAt,asc"));

        mvc.perform(as(TestUsers.withRole(Role.ADMIN), get("/api/admin/drivers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void unknownSortFieldIs400() throws Exception {
        mvc.perform(as(TestUsers.withRole(Role.ADMIN), get("/api/admin/users").param("sort", "passwordHash,asc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT_FIELD"));
    }

    @Test
    void oversizedPageIs400() throws Exception {
        mvc.perform(as(TestUsers.withRole(Role.ADMIN), get("/api/admin/users").param("size", "500")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void invalidPathUuidIs400() throws Exception {
        mvc.perform(as(TestUsers.withRole(Role.ADMIN), post("/api/admin/drivers/not-a-uuid/verify")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void adminDecisionUsesAuthenticatedAdminId() throws Exception {
        User admin = TestUsers.withRole(Role.ADMIN);
        UUID driverId = UUID.randomUUID();

        mvc.perform(as(admin, post("/api/admin/drivers/{id}/reject", driverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Licence expired\"}"))
                .andExpect(status().isOk());
        verify(driverAdministrationService).reject(eq(admin.getId()), eq(driverId), eq("Licence expired"));
    }
}
