package com.rideflow.service.driver;

import com.rideflow.dto.common.PageResponse;
import com.rideflow.dto.driver.DriverResponse;
import com.rideflow.entity.AuditAction;
import com.rideflow.entity.Driver;
import com.rideflow.entity.DriverVerificationStatus;
import com.rideflow.entity.OfflineReason;
import com.rideflow.entity.Vehicle;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.ResourceNotFoundException;
import com.rideflow.mapper.DriverMapper;
import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.RideOfferRepository;
import com.rideflow.repository.VehicleRepository;
import com.rideflow.service.audit.AuditService;
import com.rideflow.service.driver.event.DriverWentOfflineEvent;
import com.rideflow.service.event.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin-side driver management: listing and verification decisions. */
@Service
public class DriverAdministrationService {

    private static final String ENTITY_TYPE = "DRIVER";
    private static final String REASON = "reason";

    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final RideOfferRepository offers;
    private final DriverMapper driverMapper;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final Clock clock;

    public DriverAdministrationService(
            DriverRepository drivers,
            VehicleRepository vehicles,
            RideOfferRepository offers,
            DriverMapper driverMapper,
            AuditService auditService,
            DomainEventPublisher events,
            Clock clock) {
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.offers = offers;
        this.driverMapper = driverMapper;
        this.auditService = auditService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<DriverResponse> list(DriverVerificationStatus status, Pageable pageable) {
        Page<Driver> page = status == null
                ? drivers.findAllBy(pageable)
                : drivers.findByVerificationStatus(status, pageable);
        // One query for all vehicles on the page instead of one per driver.
        Map<UUID, Vehicle> activeVehicles = vehicles
                .findByDriverIdInAndActiveTrue(page.map(Driver::getId).getContent()).stream()
                .collect(Collectors.toMap(vehicle -> vehicle.getDriver().getId(), Function.identity()));
        return PageResponse.of(page, driver -> driverMapper.toResponse(driver, activeVehicles.get(driver.getId())));
    }

    @Transactional
    public DriverResponse verify(UUID adminId, UUID driverId) {
        Driver driver = load(driverId);
        driver.verify(adminId, clock.instant());
        auditService.record(adminId, AuditAction.DRIVER_VERIFIED, ENTITY_TYPE, driverId, Map.of());
        return toResponse(driver);
    }

    @Transactional
    public DriverResponse reject(UUID adminId, UUID driverId, String reason) {
        Driver driver = load(driverId);
        driver.reject(reason.trim());
        auditService.record(adminId, AuditAction.DRIVER_REJECTED, ENTITY_TYPE, driverId, Map.of(REASON, reason.trim()));
        return toResponse(driver);
    }

    @Transactional
    public DriverResponse suspend(UUID adminId, UUID driverId, String reason) {
        Driver driver = load(driverId);
        boolean wasOnline = driver.isOnline();
        Instant now = clock.instant();
        driver.suspend(reason.trim());
        offers.cancelPendingForDriver(driverId, now);
        if (wasOnline) {
            events.publish(new DriverWentOfflineEvent(driverId, OfflineReason.ACCOUNT_SUSPENDED, now));
        }
        auditService.record(adminId, AuditAction.DRIVER_SUSPENDED, ENTITY_TYPE, driverId, Map.of(REASON, reason.trim()));
        return toResponse(driver);
    }

    private Driver load(UUID driverId) {
        return drivers.findWithUserById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DRIVER_PROFILE_NOT_FOUND, "Driver not found"));
    }

    private DriverResponse toResponse(Driver driver) {
        return driverMapper.toResponse(driver, vehicles.findByDriverIdAndActiveTrue(driver.getId()).orElse(null));
    }
}
