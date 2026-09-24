package com.rideflow.websocket;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.entity.Role;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class StompAuthorizationInterceptorTest {

    private static void assertForbidden(Runnable check) {
        assertThatThrownBy(check::run)
                .isInstanceOf(RideFlowException.class)
                .extracting(ex -> ((RideFlowException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleMaySubscribeToItsOwnQueues(Role role) {
        for (String queue : StompDestinations.USER_QUEUES) {
            assertThatCode(() -> StompAuthorizationInterceptor.authorizeSubscribe("/user" + queue, role))
                    .doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // Another session's resolved queue: the classic user-destination bypass.
        "/queue/rides-user0a1b2c3d",
        "/queue/rides",
        "/user/queue/unknown",
        "/user/queue/rides/../ride-offers",
        "/user/0b8e5c1e-0000-0000-0000-000000000000/queue/rides",
        "/topic/rides/0b8e5c1e-0000-0000-0000-000000000000/location",
        "/topic/admin/activity/extra",
        "/app/drivers/location",
        ""
    })
    void anyOtherSubscriptionIsRefused(String destination) {
        assertForbidden(() -> StompAuthorizationInterceptor.authorizeSubscribe(destination, Role.ADMIN));
    }

    @Test
    void nullDestinationIsRefused() {
        assertForbidden(() -> StompAuthorizationInterceptor.authorizeSubscribe(null, Role.ADMIN));
        assertForbidden(() -> StompAuthorizationInterceptor.authorizeSend(null, Role.DRIVER));
    }

    @Test
    void onlyAdminsMaySubscribeToAdminActivity() {
        assertThatCode(() -> StompAuthorizationInterceptor.authorizeSubscribe(StompDestinations.ADMIN_ACTIVITY, Role.ADMIN))
                .doesNotThrowAnyException();
        assertForbidden(() -> StompAuthorizationInterceptor.authorizeSubscribe(StompDestinations.ADMIN_ACTIVITY, Role.PASSENGER));
        assertForbidden(() -> StompAuthorizationInterceptor.authorizeSubscribe(StompDestinations.ADMIN_ACTIVITY, Role.DRIVER));
    }

    @Test
    void onlyDriversMaySendLocations() {
        assertThatCode(() -> StompAuthorizationInterceptor.authorizeSend(StompDestinations.DRIVER_LOCATION, Role.DRIVER))
                .doesNotThrowAnyException();
        assertForbidden(() -> StompAuthorizationInterceptor.authorizeSend(StompDestinations.DRIVER_LOCATION, Role.PASSENGER));
        assertForbidden(() -> StompAuthorizationInterceptor.authorizeSend(StompDestinations.DRIVER_LOCATION, Role.ADMIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/admin/activity", "/queue/rides-user0a1b2c3d", "/user/queue/rides", "/app/other"})
    void clientsCannotSendToBrokerDestinationsOrUnknownEndpoints(String destination) {
        assertForbidden(() -> StompAuthorizationInterceptor.authorizeSend(destination, Role.DRIVER));
    }
}
