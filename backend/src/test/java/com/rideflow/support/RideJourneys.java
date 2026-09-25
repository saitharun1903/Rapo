package com.rideflow.support;

import static com.rideflow.support.GeoTestPoints.HITECH_CITY;
import static com.rideflow.support.GeoTestPoints.HUSSAIN_SAGAR;
import static com.rideflow.support.GeoTestPoints.offset;
import static com.rideflow.support.RideApi.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.rideflow.entity.PaymentMethod;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.support.RideFixtures.Actor;
import java.time.Duration;
import java.util.UUID;
import org.springframework.test.web.servlet.MvcResult;

/** Whole ride journeys over the API, for tests that need a ride in a given state. */
public final class RideJourneys {

    private static final Duration MATCHING_TIMEOUT = Duration.ofSeconds(15);
    /** Shorter than the access token lifetime, which the test clock also governs. */
    private static final Duration TRIP = Duration.ofMinutes(10);
    private static final double DRIVER_DISTANCE_METERS = 300;
    private static final double AT_PICKUP_METERS = 40;

    private RideJourneys() {
    }

    public static Actor onlineDriver(RideFixtures fixtures, RideApi api) throws Exception {
        Actor driver = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        assertStatus(api.goOnline(driver, offset(HITECH_CITY, DRIVER_DISTANCE_METERS, 0)), 200);
        return driver;
    }

    /** Books a ride and waits until matching (through Kafka) has offered it to {@code driver}. */
    public static UUID offeredRide(RideApi api, Actor passenger, Actor driver, PaymentMethod paymentMethod)
            throws Exception {
        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR, paymentMethod);
        await().atMost(MATCHING_TIMEOUT).until(() -> api.openOfferCount(driver) == 1);
        return rideId;
    }

    /** Books, matches, drives and completes a ride; returns its id. */
    public static UUID completedRide(RideApi api, MutableClock clock, Actor passenger, Actor driver,
                                     PaymentMethod paymentMethod) throws Exception {
        UUID rideId = offeredRide(api, passenger, driver, paymentMethod);
        String ride = "/api/rides/" + rideId;
        assertStatus(api.call(driver, "POST", ride + "/accept", null), 200);
        assertStatus(api.call(driver, "POST", ride + "/en-route", null), 200);
        assertStatus(api.reportLocation(driver, offset(HITECH_CITY, AT_PICKUP_METERS, 0), clock.instant()), 202);
        assertStatus(api.call(driver, "POST", ride + "/arrive", null), 200);
        assertStatus(api.call(driver, "POST", ride + "/start", null), 200);
        clock.advance(TRIP);
        assertStatus(api.reportLocation(driver, HUSSAIN_SAGAR, clock.instant()), 202);
        assertStatus(api.call(driver, "POST", ride + "/complete", null), 200);
        return rideId;
    }

    private static void assertStatus(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(status);
    }
}
