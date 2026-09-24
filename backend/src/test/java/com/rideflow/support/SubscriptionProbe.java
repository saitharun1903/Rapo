package com.rideflow.support;

import static org.awaitility.Awaitility.await;

import java.security.Principal;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.user.UserDestinationMessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;

/**
 * The simple broker sends no receipt for SUBSCRIBE, so a test cannot tell from the client when a push would
 * be delivered. This inbound-channel interceptor records a subscription once the handler that registers it
 * has run (the user-destination handler for {@code /user/...}, the broker for topics), so tests wait for
 * that instead of sleeping.
 */
public class SubscriptionProbe implements ExecutorChannelInterceptor {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_PREFIX = "/user/";

    private final Set<String> registered = ConcurrentHashMap.newKeySet();

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        if (ex != null || SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.SUBSCRIBE) {
            return;
        }
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        Principal user = SimpMessageHeaderAccessor.getUser(message.getHeaders());
        if (destination == null || user == null) {
            return;
        }
        boolean registers = destination.startsWith(USER_PREFIX)
                ? handler instanceof UserDestinationMessageHandler
                : handler instanceof SimpleBrokerMessageHandler;
        if (registers) {
            registered.add(key(user.getName(), destination));
        }
    }

    public void awaitSubscribed(UUID userId, String destination) {
        await().atMost(TIMEOUT).until(() -> registered.contains(key(userId.toString(), destination)));
    }

    private static String key(String user, String destination) {
        return user + " " + destination;
    }
}
