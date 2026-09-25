package com.rideflow.service.driver;

import com.rideflow.dto.driver.DriverProfileRequest;
import com.rideflow.dto.driver.DriverResponse;
import com.rideflow.dto.driver.VehicleRequest;
import com.rideflow.entity.AuditAction;
import com.rideflow.entity.Driver;
import com.rideflow.entity.DriverAvailability;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.entity.Vehicle;
import com.rideflow.exception.DuplicateResourceException;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import com.rideflow.exception.ResourceNotFoundException;
import com.rideflow.exception.RideFlowException;
import com.rideflow.mapper.DriverMapper;
import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.UserRepository;
import com.rideflow.repository.VehicleRepository;
import com.rideflow.service.audit.AuditService;
import com.rideflow.utility.TextNormalizer;
import java.time.Clock;
import java.time.Year;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Driver self-service: submitting the licence + vehicle profile that an admin then verifies. */
@Service
public class DriverOnboardingService {

    private static final String ENTITY_TYPE = "DRIVER";
    /** Manufacturers release next year's models during the current year. */
    private static final int MAX_MODEL_YEARS_AHEAD = 1;

    private final UserRepository users;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final DriverMapper driverMapper;
    private final AuditService auditService;
    private final Clock clock;

    public DriverOnboardingService(
            UserRepository users,
            DriverRepository drivers,
            VehicleRepository vehicles,
            DriverMapper driverMapper,
            AuditService auditService,
            Clock clock) {
        this.users = users;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.driverMapper = driverMapper;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public DriverResponse submitProfile(UUID userId, DriverProfileRequest request) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        if (user.getRole() != Role.DRIVER) {
            throw new RideFlowException(ErrorCode.FORBIDDEN, "Only driver accounts can submit a driver profile");
        }
        if (drivers.existsById(userId)) {
            throw new DuplicateResourceException(ErrorCode.DRIVER_PROFILE_EXISTS, "Driver profile already submitted");
        }
        String licenseNumber = TextNormalizer.identifier(request.licenseNumber());
        if (drivers.existsByLicenseNumber(licenseNumber)) {
            throw new DuplicateResourceException(ErrorCode.LICENSE_TAKEN, "This licence number is already registered");
        }
        Vehicle.VehicleDetails details = toVehicleDetails(request.vehicle());
        if (vehicles.existsByPlateNumber(details.plateNumber())) {
            throw new DuplicateResourceException(ErrorCode.PLATE_TAKEN, "This plate number is already registered");
        }

        Driver driver = drivers.save(Driver.onboard(user, licenseNumber));
        Vehicle vehicle = vehicles.save(Vehicle.register(driver, details));
        auditService.record(userId, AuditAction.DRIVER_PROFILE_SUBMITTED, ENTITY_TYPE, userId, Map.of());
        return driverMapper.toResponse(driver, vehicle);
    }

    /**
     * Replaces the driver's active vehicle. Only while offline, so a driver can never be matched, or be on a
     * trip, in a vehicle that is being swapped out. Plates are unique across all vehicles ever registered, so a
     * retired vehicle's plate cannot be registered again.
     */
    @Transactional
    public DriverResponse replaceVehicle(UUID driverId, VehicleRequest request) {
        Driver driver = drivers.findWithUserById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DRIVER_PROFILE_NOT_FOUND,
                        "Driver profile has not been submitted yet"));
        if (driver.getAvailability() != DriverAvailability.OFFLINE) {
            throw new InvalidStateException(ErrorCode.INVALID_DRIVER_STATE, "Go offline before changing vehicle");
        }
        Vehicle.VehicleDetails details = toVehicleDetails(request);
        if (vehicles.existsByPlateNumber(details.plateNumber())) {
            throw new DuplicateResourceException(ErrorCode.PLATE_TAKEN, "This plate number is already registered");
        }
        vehicles.findByDriverIdAndActiveTrue(driverId).ifPresent(current -> {
            current.deactivate();
            // One active vehicle per driver is a unique index; retire the old one before inserting the new.
            vehicles.flush();
        });
        Vehicle vehicle = vehicles.save(Vehicle.register(driver, details));
        auditService.record(driverId, AuditAction.DRIVER_VEHICLE_REPLACED, ENTITY_TYPE, driverId,
                Map.of("vehicleId", vehicle.getId().toString(), "category", details.category().name()));
        return driverMapper.toResponse(driver, vehicle);
    }

    @Transactional(readOnly = true)
    public DriverResponse getProfile(UUID driverId) {
        Driver driver = drivers.findWithUserById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DRIVER_PROFILE_NOT_FOUND,
                        "Driver profile has not been submitted yet"));
        return driverMapper.toResponse(driver, vehicles.findByDriverIdAndActiveTrue(driverId).orElse(null));
    }

    private Vehicle.VehicleDetails toVehicleDetails(VehicleRequest request) {
        int latestAllowedYear = Year.now(clock).getValue() + MAX_MODEL_YEARS_AHEAD;
        if (request.modelYear() > latestAllowedYear) {
            throw new RideFlowException(ErrorCode.INVALID_MODEL_YEAR,
                    "Model year cannot be later than " + latestAllowedYear);
        }
        return new Vehicle.VehicleDetails(
                request.make().trim(),
                request.model().trim(),
                request.color().trim(),
                TextNormalizer.identifier(request.plateNumber()),
                request.modelYear(),
                request.category(),
                request.seats());
    }
}
