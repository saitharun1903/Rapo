package com.rideflow.websocket;

import java.util.Set;

/**
 * Every STOMP destination the server accepts or pushes to (contract: docs/events.md section 2). The
 * subscription and send rules in {@link StompAuthorizationInterceptor} are an allow-list built from these.
 */
public final class StompDestinations {

    /** WebSocket handshake path (native WebSocket, no SockJS). */
    public static final String ENDPOINT = "/ws";

    public static final String APP_PREFIX = "/app";
    public static final String USER_PREFIX = "/user";
    public static final String TOPIC_PREFIX = "/topic";
    public static final String QUEUE_PREFIX = "/queue";

    /** Client → server: driver GPS reports ({@code @MessageMapping} value, without the /app prefix). */
    public static final String DRIVER_LOCATION_MAPPING = "/drivers/location";
    public static final String DRIVER_LOCATION = APP_PREFIX + DRIVER_LOCATION_MAPPING;

    /** Server → user queues. Clients subscribe with the /user prefix; Spring routes to the right sessions. */
    public static final String RIDE_UPDATES = "/queue/rides";
    public static final String RIDE_LOCATION = "/queue/ride-location";
    public static final String RIDE_OFFERS = "/queue/ride-offers";
    public static final String PRESENCE = "/queue/presence";
    public static final String ERRORS = "/queue/errors";
    public static final String NOTIFICATIONS = "/queue/notifications";

    public static final Set<String> USER_QUEUES =
            Set.of(RIDE_UPDATES, RIDE_LOCATION, RIDE_OFFERS, PRESENCE, ERRORS, NOTIFICATIONS);

    /** Server → admins: every ride status change. */
    public static final String ADMIN_ACTIVITY = "/topic/admin/activity";

    private StompDestinations() {
    }
}
