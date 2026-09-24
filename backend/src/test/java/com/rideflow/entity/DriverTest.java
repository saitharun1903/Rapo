package com.rideflow.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DriverTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    private static Driver newDriver() {
        User user = User.register("d@example.com", null, "{bcrypt}hash", "Test Driver", Role.DRIVER);
        return Driver.onboard(user, "LIC123456");
    }

    @Test
    void newDriverIsPendingAndOffline() {
        Driver driver = newDriver();

        assertThat(driver.getVerificationStatus()).isEqualTo(DriverVerificationStatus.PENDING);
        assertThat(driver.getAvailability()).isEqualTo(DriverAvailability.OFFLINE);
    }

    @Test
    void verifyRecordsAdminAndTime() {
        Driver driver = newDriver();

        driver.verify(ADMIN_ID, NOW);

        assertThat(driver.getVerificationStatus()).isEqualTo(DriverVerificationStatus.VERIFIED);
        assertThat(driver.getVerifiedBy()).isEqualTo(ADMIN_ID);
        assertThat(driver.getVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectedDriverCanLaterBeVerifiedAndReasonIsCleared() {
        Driver driver = newDriver();
        driver.reject("Blurry licence photo");

        driver.verify(ADMIN_ID, NOW);

        assertThat(driver.getVerificationStatus()).isEqualTo(DriverVerificationStatus.VERIFIED);
        assertThat(driver.getRejectionReason()).isNull();
    }

    @Test
    void suspendedDriverCanBeReinstated() {
        Driver driver = newDriver();
        driver.verify(ADMIN_ID, NOW);
        driver.suspend("Complaint under review");

        assertThat(driver.getVerificationStatus()).isEqualTo(DriverVerificationStatus.SUSPENDED);
        assertThat(driver.getAvailability()).isEqualTo(DriverAvailability.OFFLINE);

        driver.verify(ADMIN_ID, NOW);
        assertThat(driver.getVerificationStatus()).isEqualTo(DriverVerificationStatus.VERIFIED);
    }

    @Test
    void cannotVerifyAlreadyVerifiedDriver() {
        Driver driver = newDriver();
        driver.verify(ADMIN_ID, NOW);

        assertInvalidState(() -> driver.verify(ADMIN_ID, NOW));
    }

    @Test
    void cannotRejectVerifiedDriver() {
        Driver driver = newDriver();
        driver.verify(ADMIN_ID, NOW);

        assertInvalidState(() -> driver.reject("too late"));
    }

    @Test
    void cannotSuspendPendingDriver() {
        assertInvalidState(() -> newDriver().suspend("not verified yet"));
    }

    private static void assertInvalidState(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(InvalidStateException.class)
                .extracting(ex -> ((InvalidStateException) ex).code())
                .isEqualTo(ErrorCode.INVALID_DRIVER_STATE);
    }
}
