package com.rideflow.websocket;

import com.rideflow.dto.driver.LocationUpdateRequest;
import com.rideflow.dto.realtime.StompErrorMessage;
import com.rideflow.exception.ApiError;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.driver.DriverLocationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/**
 * The driver GPS stream ({@code SEND /app/drivers/location}). Only drivers reach this handler
 * ({@link StompAuthorizationInterceptor}). A rejected report is answered on the sender's own
 * {@code /user/queue/errors} and the connection stays open, because one bad fix must not interrupt the stream.
 */
@Controller
public class DriverLocationMessageController {

    private static final Logger log = LoggerFactory.getLogger(DriverLocationMessageController.class);

    private final DriverLocationService locations;
    private final LocationThrottle throttle;

    public DriverLocationMessageController(DriverLocationService locations, LocationThrottle throttle) {
        this.locations = locations;
        this.throttle = throttle;
    }

    @MessageMapping(StompDestinations.DRIVER_LOCATION_MAPPING)
    public void report(@Valid @Payload LocationUpdateRequest update, Authentication authentication,
                       SimpMessageHeaderAccessor headers) {
        Map<String, Object> session = headers.getSessionAttributes();
        if (session != null && !throttle.tryAcquire(session)) {
            return;
        }
        AuthenticatedUser driver = (AuthenticatedUser) authentication.getPrincipal();
        locations.report(driver.id(), update);
    }

    @MessageExceptionHandler
    @SendToUser(destinations = StompDestinations.ERRORS, broadcast = false)
    public StompErrorMessage handleDomain(RideFlowException ex) {
        log.debug("Location message rejected with {}: {}", ex.code(), ex.getMessage());
        return error(ex.code(), ex.getMessage(), List.of());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = StompDestinations.ERRORS, broadcast = false)
    public StompErrorMessage handleInvalid(MethodArgumentNotValidException ex) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult() == null ? List.of()
                : ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                        .toList();
        return error(ErrorCode.VALIDATION_FAILED, "Location message validation failed", violations);
    }

    @MessageExceptionHandler
    @SendToUser(destinations = StompDestinations.ERRORS, broadcast = false)
    public StompErrorMessage handleUnreadable(MessageConversionException ex) {
        log.debug("Unreadable location message: {}", ex.getMessage());
        return error(ErrorCode.MALFORMED_REQUEST, "Location message is not valid JSON of the expected shape", List.of());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = StompDestinations.ERRORS, broadcast = false)
    public StompErrorMessage handleUnexpected(Exception ex) {
        log.error("Unexpected error handling a driver location message", ex);
        return error(ErrorCode.INTERNAL_ERROR, "Unexpected server error", List.of());
    }

    private static StompErrorMessage error(ErrorCode code, String message, List<ApiError.FieldViolation> violations) {
        return new StompErrorMessage(code.name(), message, StompDestinations.DRIVER_LOCATION, violations);
    }
}
