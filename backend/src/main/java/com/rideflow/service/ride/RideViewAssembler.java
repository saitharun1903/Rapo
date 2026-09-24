package com.rideflow.service.ride;

import com.rideflow.dto.common.Money;
import com.rideflow.dto.ride.RideResponse;
import com.rideflow.dto.ride.RideSummaryResponse;
import com.rideflow.entity.Driver;
import com.rideflow.entity.FareBreakdown;
import com.rideflow.entity.FareKind;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import com.rideflow.entity.Vehicle;
import com.rideflow.mapper.FareMapper;
import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.FareBreakdownRepository;
import com.rideflow.repository.PaymentRepository;
import com.rideflow.repository.UserRepository;
import com.rideflow.repository.VehicleRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Builds ride API views; loads the related driver, vehicle, passenger and fare snapshots. */
@Component
public class RideViewAssembler {

    private final FareBreakdownRepository fareBreakdowns;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final UserRepository users;
    private final PaymentRepository payments;
    private final FareMapper fareMapper;

    public RideViewAssembler(FareBreakdownRepository fareBreakdowns, DriverRepository drivers,
                             VehicleRepository vehicles, UserRepository users, PaymentRepository payments,
                             FareMapper fareMapper) {
        this.fareBreakdowns = fareBreakdowns;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.users = users;
        this.payments = payments;
        this.fareMapper = fareMapper;
    }

    public RideResponse toResponse(Ride ride) {
        Map<FareKind, FareBreakdown> fares = fareBreakdowns.findByRideId(ride.getId()).stream()
                .collect(Collectors.toMap(FareBreakdown::getKind, Function.identity()));
        return new RideResponse(
                ride.getId(),
                ride.getStatus(),
                ride.getVersion(),
                ride.getVehicleCategory(),
                new RideResponse.Place(ride.getPickup(), ride.getPickupAddress()),
                new RideResponse.Place(ride.getDropoff(), ride.getDropoffAddress()),
                ride.getPaymentMethod(),
                estimate(ride, fares.get(FareKind.ESTIMATE)),
                actual(ride, fares.get(FareKind.FINAL)),
                paymentInfo(ride),
                driverInfo(ride),
                users.findById(ride.getPassengerId())
                        .map(user -> new RideResponse.PassengerInfo(user.getId(), user.getFullName()))
                        .orElse(null),
                new RideResponse.Matching(ride.getMatchingRound(), ride.getMatchingRadiusMeters()),
                new RideResponse.Timestamps(ride.getRequestedAt(), ride.getAcceptedAt(), ride.getEnRouteAt(),
                        ride.getArrivedAt(), ride.getStartedAt(), ride.getCompletedAt(), ride.getCancelledAt(),
                        ride.getExpiredAt()),
                ride.getCancelledBy() == null ? null
                        : new RideResponse.Cancellation(ride.getCancelledBy(), ride.getCancellationReason()));
    }

    private RideResponse.PaymentInfo paymentInfo(Ride ride) {
        if (ride.getStatus() != RideStatus.COMPLETED) {
            return null;
        }
        return payments.findByRideId(ride.getId())
                .map(payment -> new RideResponse.PaymentInfo(payment.getId(), payment.getMethod(), payment.getStatus(),
                        payment.getProvider(), Money.of(payment.getAmount(), payment.getCurrency())))
                .orElse(null);
    }

    /** Summaries for a page of rides, loading all fare snapshots in one query. */
    public List<RideSummaryResponse> toSummaries(Page<Ride> page) {
        List<UUID> rideIds = page.map(Ride::getId).getContent();
        Map<UUID, Map<FareKind, FareBreakdown>> faresByRide = fareBreakdowns.findByRideIdIn(rideIds).stream()
                .collect(Collectors.groupingBy(FareBreakdown::getRideId,
                        Collectors.toMap(FareBreakdown::getKind, Function.identity())));
        return page.getContent().stream().map(ride -> {
            Map<FareKind, FareBreakdown> fares = faresByRide.getOrDefault(ride.getId(), Map.of());
            FareBreakdown shown = fares.getOrDefault(FareKind.FINAL, fares.get(FareKind.ESTIMATE));
            return new RideSummaryResponse(ride.getId(), ride.getStatus(), ride.getVehicleCategory(),
                    ride.getPickupAddress(), ride.getDropoffAddress(),
                    shown == null ? null : Money.of(shown.getTotal(), ride.getCurrency()),
                    fares.containsKey(FareKind.FINAL), ride.getRequestedAt(), ride.getCompletedAt());
        }).toList();
    }

    public Money estimatedFare(Ride ride) {
        return fareBreakdowns.findByRideId(ride.getId()).stream()
                .filter(breakdown -> breakdown.getKind() == FareKind.ESTIMATE)
                .findFirst()
                .map(breakdown -> Money.of(breakdown.getTotal(), ride.getCurrency()))
                .orElse(null);
    }

    private RideResponse.Estimate estimate(Ride ride, FareBreakdown breakdown) {
        return new RideResponse.Estimate(ride.getEstimatedDistanceMeters(), ride.getEstimatedDurationSeconds(),
                ride.getEstimateSource(),
                breakdown == null ? null : Money.of(breakdown.getTotal(), ride.getCurrency()),
                breakdown == null ? null : fareMapper.toResponse(breakdown.amounts()));
    }

    private RideResponse.Actual actual(Ride ride, FareBreakdown breakdown) {
        if (breakdown == null || ride.getActualDistanceMeters() == null) {
            return null;
        }
        return new RideResponse.Actual(ride.getActualDistanceMeters(), ride.getActualDurationSeconds(),
                ride.getDistanceSource(), Money.of(breakdown.getTotal(), ride.getCurrency()),
                fareMapper.toResponse(breakdown.amounts()));
    }

    private RideResponse.DriverInfo driverInfo(Ride ride) {
        if (ride.getDriverId() == null) {
            return null;
        }
        Driver driver = drivers.findWithUserById(ride.getDriverId()).orElse(null);
        if (driver == null) {
            return null;
        }
        Vehicle vehicle = ride.getVehicleId() == null ? null : vehicles.findById(ride.getVehicleId()).orElse(null);
        return new RideResponse.DriverInfo(driver.getId(), driver.getUser().getFullName(), driver.getRatingAvg(),
                driver.getRatingCount(), vehicle == null ? null : new RideResponse.Vehicle(vehicle.getMake(),
                        vehicle.getModel(), vehicle.getColor(), vehicle.getPlateNumber(), vehicle.getCategory()));
    }
}
