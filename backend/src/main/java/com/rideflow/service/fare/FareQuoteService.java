package com.rideflow.service.fare;

import com.rideflow.config.RideProperties;
import com.rideflow.dto.common.Money;
import com.rideflow.dto.fare.FareEstimateResponse;
import com.rideflow.dto.fare.FareQuoteResponse;
import com.rideflow.entity.FareBreakdown;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import com.rideflow.geospatial.GeoMath;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.geospatial.RouteEstimate;
import com.rideflow.geospatial.RoutingService;
import com.rideflow.mapper.FareMapper;
import com.rideflow.service.ride.ServiceAreaPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Produces signed, per-category fare quotes and validates them again at booking time. */
@Service
public class FareQuoteService {

    private final ServiceAreaPolicy serviceArea;
    private final RoutingService routing;
    private final SurgeService surge;
    private final FareCalculator calculator;
    private final FareQuoteSigner signer;
    private final FareMapper fareMapper;
    private final RideProperties rideProperties;
    private final Clock clock;

    public FareQuoteService(ServiceAreaPolicy serviceArea, RoutingService routing, SurgeService surge,
                            FareCalculator calculator, FareQuoteSigner signer, FareMapper fareMapper,
                            RideProperties rideProperties, Clock clock) {
        this.serviceArea = serviceArea;
        this.routing = routing;
        this.surge = surge;
        this.calculator = calculator;
        this.signer = signer;
        this.fareMapper = fareMapper;
        this.rideProperties = rideProperties;
        this.clock = clock;
    }

    public FareEstimateResponse estimate(UUID passengerId, GeoPoint pickup, GeoPoint dropoff) {
        serviceArea.validateTrip(pickup, dropoff);
        RouteEstimate route = routing.route(pickup, dropoff);
        BigDecimal surgeMultiplier = surge.multiplierAt(pickup);
        Instant expiresAt = clock.instant().plus(rideProperties.quoteTtl());

        List<FareQuoteResponse> quotes = Arrays.stream(VehicleCategory.values())
                .map(category -> quote(passengerId, category, pickup, dropoff, route, surgeMultiplier, expiresAt))
                .toList();
        return new FareEstimateResponse(route.distanceMeters(), route.durationSeconds(), route.source(),
                surgeMultiplier.toPlainString(), route.path(), quotes);
    }

    /**
     * Verifies a quote presented at booking: genuine signature, not expired, issued to this passenger, and
     * for the same pickup/dropoff (within tolerance, since clients may re-send slightly rounded coordinates).
     */
    public FareQuote redeem(UUID passengerId, String quoteToken, GeoPoint pickup, GeoPoint dropoff) {
        FareQuote quote = signer.verify(quoteToken);
        if (!quote.passengerId().equals(passengerId)) {
            throw new RideFlowException(ErrorCode.QUOTE_INVALID, "Fare quote is invalid; request a new estimate");
        }
        if (!clock.instant().isBefore(quote.expiresAt())) {
            throw new RideFlowException(ErrorCode.QUOTE_EXPIRED, "Fare quote has expired; request a new estimate");
        }
        int tolerance = rideProperties.quoteMatchToleranceMeters();
        if (GeoMath.haversineMeters(quote.pickup(), pickup) > tolerance
                || GeoMath.haversineMeters(quote.dropoff(), dropoff) > tolerance) {
            throw new RideFlowException(ErrorCode.QUOTE_MISMATCH,
                    "Pickup or destination changed since the quote; request a new estimate");
        }
        return quote;
    }

    private FareQuoteResponse quote(UUID passengerId, VehicleCategory category, GeoPoint pickup, GeoPoint dropoff,
                                    RouteEstimate route, BigDecimal surgeMultiplier, Instant expiresAt) {
        FareBreakdown.Amounts fare = calculator.calculate(
                category, route.distanceMeters(), route.durationSeconds(), surgeMultiplier);
        String token = signer.sign(new FareQuote(FareQuote.CURRENT_SCHEMA_VERSION, passengerId, category, pickup,
                dropoff, route.distanceMeters(), route.durationSeconds(), route.source(), fare, expiresAt));
        return new FareQuoteResponse(token, category, Money.of(fare.total(), fare.currency()),
                Money.of(fare.minimumFare(), fare.currency()), fareMapper.toResponse(fare), expiresAt);
    }
}
