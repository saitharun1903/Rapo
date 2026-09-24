package com.rideflow.websocket;

import com.rideflow.entity.Role;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import com.rideflow.security.AuthenticatedUser;
import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Deny-by-default rules for client frames, applied after {@link StompAuthenticationInterceptor}:
 * <ul>
 *   <li>SUBSCRIBE: the caller's own {@code /user/queue/*} destinations; {@code /topic/admin/activity} for
 *       admins. Nothing else, so clients cannot subscribe to another session's resolved queue
 *       ({@code /queue/...-user<session>}) or invent topics.</li>
 *   <li>SEND: only {@code /app/drivers/location}, drivers only. Clients can never send straight to a broker
 *       destination, which would let them inject messages into other users' streams.</li>
 * </ul>
 */
@Component
public class StompAuthorizationInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscribe(accessor.getDestination(), roleOf(accessor.getUser()));
        } else if (command == StompCommand.SEND) {
            authorizeSend(accessor.getDestination(), roleOf(accessor.getUser()));
        }
        return message;
    }

    static void authorizeSubscribe(String destination, Role role) {
        boolean ownQueue = destination != null && destination.startsWith(StompDestinations.USER_PREFIX)
                && StompDestinations.USER_QUEUES.contains(destination.substring(StompDestinations.USER_PREFIX.length()));
        boolean adminActivity = StompDestinations.ADMIN_ACTIVITY.equals(destination) && role == Role.ADMIN;
        if (!ownQueue && !adminActivity) {
            throw forbidden("subscribe to", destination);
        }
    }

    static void authorizeSend(String destination, Role role) {
        if (!(StompDestinations.DRIVER_LOCATION.equals(destination) && role == Role.DRIVER)) {
            throw forbidden("send to", destination);
        }
    }

    private static Role roleOf(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.role();
        }
        throw new RideFlowException(ErrorCode.UNAUTHENTICATED, "Send CONNECT with an access token first");
    }

    private static RideFlowException forbidden(String action, String destination) {
        return new RideFlowException(ErrorCode.FORBIDDEN, "Not allowed to " + action + " " + destination);
    }
}
