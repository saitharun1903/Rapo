package com.rideflow.service.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rideflow.dto.driver.DriverProfileRequest;
import com.rideflow.dto.driver.VehicleRequest;
import com.rideflow.entity.Driver;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.entity.Vehicle;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import com.rideflow.mapper.DriverMapper;
import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.UserRepository;
import com.rideflow.repository.VehicleRepository;
import com.rideflow.service.audit.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DriverOnboardingServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserRepository users;
    @Mock
    private DriverRepository drivers;
    @Mock
    private VehicleRepository vehicles;
    @Mock
    private DriverMapper driverMapper;
    @Mock
    private AuditService auditService;

    private DriverOnboardingService service;
    private User driverUser;

    @BeforeEach
    void setUp() {
        service = new DriverOnboardingService(users, drivers, vehicles, driverMapper, auditService, CLOCK);
        driverUser = User.register("d@example.com", null, "{bcrypt}x", "D", Role.DRIVER);
        ReflectionTestUtils.setField(driverUser, "id", UUID.randomUUID());
    }

    private static DriverProfileRequest request(short modelYear) {
        return new DriverProfileRequest("ts-01 2019 0001",
                new VehicleRequest(" Honda ", "City", "Grey", "ts 09 ab 1234", modelYear, VehicleCategory.COMFORT, (short) 4));
    }

    @Test
    void storesNormalisedLicenceAndPlate() {
        when(users.findById(driverUser.getId())).thenReturn(Optional.of(driverUser));
        when(drivers.save(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicles.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.submitProfile(driverUser.getId(), request((short) 2027));

        ArgumentCaptor<Driver> driver = ArgumentCaptor.forClass(Driver.class);
        ArgumentCaptor<Vehicle> vehicle = ArgumentCaptor.forClass(Vehicle.class);
        verify(drivers).save(driver.capture());
        verify(vehicles).save(vehicle.capture());
        assertThat(driver.getValue().getLicenseNumber()).isEqualTo("TS0120190001");
        assertThat(vehicle.getValue().getPlateNumber()).isEqualTo("TS09AB1234");
        assertThat(vehicle.getValue().getMake()).isEqualTo("Honda");
        assertThat(vehicle.getValue().isActive()).isTrue();
    }

    @Test
    void rejectsModelYearMoreThanOneYearAhead() {
        when(users.findById(driverUser.getId())).thenReturn(Optional.of(driverUser));

        assertThatThrownBy(() -> service.submitProfile(driverUser.getId(), request((short) 2028)))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.INVALID_MODEL_YEAR);
        verify(drivers, never()).save(any());
    }

    @Test
    void rejectsSecondProfile() {
        when(users.findById(driverUser.getId())).thenReturn(Optional.of(driverUser));
        when(drivers.existsById(driverUser.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.submitProfile(driverUser.getId(), request((short) 2022)))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.DRIVER_PROFILE_EXISTS);
    }

    @Test
    void rejectsDuplicatePlate() {
        when(users.findById(driverUser.getId())).thenReturn(Optional.of(driverUser));
        when(vehicles.existsByPlateNumber("TS09AB1234")).thenReturn(true);

        assertThatThrownBy(() -> service.submitProfile(driverUser.getId(), request((short) 2022)))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.PLATE_TAKEN);
    }

    @Test
    void passengerAccountCannotSubmitDriverProfile() {
        User passenger = User.register("p@example.com", null, "{bcrypt}x", "P", Role.PASSENGER);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(passenger, "id", id);
        when(users.findById(id)).thenReturn(Optional.of(passenger));

        assertThatThrownBy(() -> service.submitProfile(id, request((short) 2022)))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
