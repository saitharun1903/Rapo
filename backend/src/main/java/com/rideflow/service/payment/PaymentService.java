package com.rideflow.service.payment;

import com.rideflow.entity.FareBreakdown;
import com.rideflow.entity.FareKind;
import com.rideflow.entity.Payment;
import com.rideflow.entity.PaymentMethod;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import com.rideflow.repository.FareBreakdownRepository;
import com.rideflow.repository.PaymentRepository;
import com.rideflow.repository.RideRepository;
import com.rideflow.service.event.DomainEventPublisher;
import com.rideflow.service.event.ProcessedEvents;
import com.rideflow.service.payment.PaymentGateway.ChargeRequest;
import com.rideflow.service.payment.PaymentGateway.ChargeResult;
import com.rideflow.service.payment.event.PaymentCreatedEvent;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settles completed rides, from {@code ride.completed}: charges the final fare through the gateway for the
 * ride's payment method, records the payment with the platform/driver split, and publishes
 * {@code payment.created}. Runs asynchronously so a slow or failing payment never fails the driver's
 * "complete" request.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    /** Idempotency key of the payments consumer ({@code processed_events.consumer}). */
    private static final String CONSUMER = "payments";

    private final RideRepository rides;
    private final FareBreakdownRepository fareBreakdowns;
    private final PaymentRepository payments;
    private final FareSplitter splitter;
    private final Map<PaymentMethod, PaymentGateway> gateways = new EnumMap<>(PaymentMethod.class);
    private final ProcessedEvents processedEvents;
    private final DomainEventPublisher events;
    private final Clock clock;

    public PaymentService(RideRepository rides, FareBreakdownRepository fareBreakdowns, PaymentRepository payments,
                          FareSplitter splitter, List<PaymentGateway> gateways, ProcessedEvents processedEvents,
                          DomainEventPublisher events, Clock clock) {
        this.rides = rides;
        this.fareBreakdowns = fareBreakdowns;
        this.payments = payments;
        this.splitter = splitter;
        gateways.forEach(gateway -> this.gateways.put(gateway.method(), gateway));
        for (PaymentMethod method : PaymentMethod.values()) {
            if (!this.gateways.containsKey(method)) {
                throw new IllegalStateException("No payment gateway for " + method);
            }
        }
        this.processedEvents = processedEvents;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Idempotent: a redelivered event is skipped, and the unique {@code payments.ride_id} guarantees one payment
     * per ride even for a different event about the same ride.
     */
    @Transactional
    public void settleCompletedRide(UUID eventId, UUID rideId) {
        if (!processedEvents.firstDelivery(CONSUMER, eventId) || payments.existsByRideId(rideId)) {
            return;
        }
        Ride ride = rides.findById(rideId).orElse(null);
        if (ride == null || ride.getStatus() != RideStatus.COMPLETED) {
            log.warn("Not settling ride {}: it is {}", rideId, ride == null ? "unknown" : ride.getStatus());
            return;
        }
        // Written in the same transaction as the COMPLETED status; its absence is a bug worth retrying and then
        // dead-lettering, not skipping.
        FareBreakdown fare = fareBreakdowns.findByRideIdAndKind(rideId, FareKind.FINAL).orElseThrow(() ->
                new IllegalStateException("Completed ride " + rideId + " has no final fare"));
        Payment.Split split = splitter.split(fare.getTotal(), fare.amounts().currency());
        ChargeResult charge = gateways.get(ride.getPaymentMethod())
                .charge(new ChargeRequest(rideId, split.amount(), split.currency()));
        Payment payment = payments.save(Payment.of(rideId, ride.getPaymentMethod(), split, charge.provider(),
                charge.status(), charge.reference()));
        events.publish(new PaymentCreatedEvent(payment.getId(), rideId, ride.getPassengerId(), ride.getDriverId(),
                payment.getAmount(), payment.getCurrency(), payment.getMethod(), payment.getStatus(),
                payment.getDriverEarnings(), clock.instant()));
        log.info("Ride {} settled: {} {} by {} ({}), driver earns {}", rideId, payment.getAmount(),
                payment.getCurrency(), payment.getMethod(), payment.getStatus(), payment.getDriverEarnings());
    }
}
