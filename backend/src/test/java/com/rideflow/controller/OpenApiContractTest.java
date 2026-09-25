package com.rideflow.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rideflow.config.OpenApiConfig;
import com.rideflow.security.RefreshTokenCookies;
import com.rideflow.service.admin.AdminReportService;
import com.rideflow.service.admin.AdminRideService;
import com.rideflow.service.admin.SystemStatusService;
import com.rideflow.service.ai.TripInsightsService;
import com.rideflow.service.audit.AuditService;
import com.rideflow.service.auth.AuthService;
import com.rideflow.service.driver.DriverAdministrationService;
import com.rideflow.service.driver.DriverAvailabilityService;
import com.rideflow.service.driver.DriverEarningsService;
import com.rideflow.service.driver.DriverLocationService;
import com.rideflow.service.driver.DriverOnboardingService;
import com.rideflow.service.driver.NearbyDriverService;
import com.rideflow.service.fare.FareQuoteService;
import com.rideflow.service.geo.GeocodingService;
import com.rideflow.service.geo.RouteService;
import com.rideflow.service.matching.OfferQueryService;
import com.rideflow.service.notification.NotificationService;
import com.rideflow.service.rating.RatingService;
import com.rideflow.service.ride.DriverRideService;
import com.rideflow.service.ride.RideBookingService;
import com.rideflow.service.ride.RideCancellationService;
import com.rideflow.service.ride.RideQueryService;
import com.rideflow.service.ride.RideTrackingService;
import com.rideflow.service.user.UserAdministrationService;
import com.rideflow.service.user.UserService;
import com.rideflow.support.WebSecurityTestConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The REST contract the frontend is typed against. {@code docs/openapi.json} is generated from the controllers
 * and DTOs by springdoc; this test fails when the code no longer matches the committed file, so a contract
 * change is always a visible diff. After an intended change, regenerate it with
 * {@code ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.write=true} and then {@code npm run api:types} in
 * {@code frontend/}.
 */
// STOMP message controllers are @Controllers too, but not part of the REST contract.
@WebMvcTest(excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.rideflow\\.websocket\\..*"))
@Import({WebSecurityTestConfig.class, OpenApiConfig.class})
// The springdoc auto-configurations the application gets; the security one hides @AuthenticationPrincipal.
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
    SpringDocSecurityConfiguration.class, SpringDocSpecPropertiesConfiguration.class,
    SpringDocWebMvcConfiguration.class})
@ActiveProfiles("test")
@MockitoBean(types = {
    AdminReportService.class, AdminRideService.class, AuditService.class, AuthService.class,
    DriverAdministrationService.class, DriverAvailabilityService.class, DriverEarningsService.class,
    DriverLocationService.class, DriverOnboardingService.class, DriverRideService.class, FareQuoteService.class,
    GeocodingService.class, NearbyDriverService.class, NotificationService.class, OfferQueryService.class,
    RatingService.class, RefreshTokenCookies.class, RideBookingService.class, RideCancellationService.class,
    RideQueryService.class, RideTrackingService.class, RouteService.class, SystemStatusService.class,
    TripInsightsService.class, UserAdministrationService.class, UserService.class})
class OpenApiContractTest {

    private static final Path CONTRACT = Path.of("..", "docs", "openapi.json");
    private static final String WRITE_PROPERTY = "openapi.write";

    private final JsonMapper json = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Autowired
    private MockMvc mvc;

    @Test
    void committedContractMatchesTheCode() throws Exception {
        String generated = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode spec = json.readTree(generated);
        if (Boolean.getBoolean(WRITE_PROPERTY)) {
            Files.writeString(CONTRACT, json.writeValueAsString(json.treeToValue(spec, Object.class)) + "\n");
        }
        assertThat(Files.exists(CONTRACT)).as("%s is missing; generate it with -D%s=true", CONTRACT, WRITE_PROPERTY)
                .isTrue();
        assertThat(json.readTree(Files.readString(CONTRACT)))
                .as("docs/openapi.json is out of date; regenerate it with -D%s=true", WRITE_PROPERTY)
                .isEqualTo(spec);
    }
}
