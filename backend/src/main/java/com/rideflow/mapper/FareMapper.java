package com.rideflow.mapper;

import com.rideflow.dto.fare.FareBreakdownResponse;
import com.rideflow.entity.FareBreakdown;
import org.springframework.stereotype.Component;

@Component
public class FareMapper {

    public FareBreakdownResponse toResponse(FareBreakdown.Amounts amounts) {
        return new FareBreakdownResponse(
                amounts.baseFare().toPlainString(),
                amounts.distanceCharge().toPlainString(),
                amounts.timeCharge().toPlainString(),
                amounts.subtotal().toPlainString(),
                amounts.surgeMultiplier().toPlainString(),
                amounts.bookingFee().toPlainString(),
                amounts.minimumFare().toPlainString(),
                amounts.minimumFareApplied(),
                amounts.total().toPlainString(),
                amounts.currency(),
                amounts.distanceMeters(),
                amounts.durationSeconds(),
                amounts.pricingVersion());
    }
}
