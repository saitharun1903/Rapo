package com.rideflow.dto.ride;

import com.rideflow.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code quoteId} is the signed quote from {@code POST /api/fares/estimate}; it fixes category and price. */
public record BookRideRequest(
        @NotBlank @Size(max = 4096) String quoteId,
        @NotNull @Valid PlaceRequest pickup,
        @NotNull @Valid PlaceRequest dropoff,
        @NotNull PaymentMethod paymentMethod) {
}
