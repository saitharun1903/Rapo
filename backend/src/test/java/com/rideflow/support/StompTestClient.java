package com.rideflow.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideflow.support.RideFixtures.Actor;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;

/** A real STOMP-over-WebSocket client for integration tests, speaking to the running server. */
public class StompTestClient implements AutoCloseable {

    public static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** How long to listen before concluding that a message was (correctly) not delivered. */
    public static final Duration SILENCE = Duration.ofMillis(750);

    private final WebSocketStompClient client;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private final String url;
    private final SubscriptionProbe probe;
    private final List<Connection> connections = new CopyOnWriteArrayList<>();

    public StompTestClient(int port, SubscriptionProbe probe) {
        this.url = "ws://localhost:" + port + "/ws";
        this.probe = probe;
        scheduler.setThreadNamePrefix("stomp-test-");
        scheduler.initialize();
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        client.setTaskScheduler(scheduler);
    }

    /** Connects with the actor's access token and waits for CONNECTED. */
    public Connection connect(Actor actor) throws Exception {
        Connection connection = open(actor, actor.bearer());
        connection.awaitConnected();
        return connection;
    }

    /** Sends CONNECT with the given Authorization header (or none) without waiting for CONNECTED. */
    public Connection connectRaw(String authorization) {
        return open(null, authorization, new WebSocketHttpHeaders());
    }

    /** Opens the handshake with a browser Origin header, without waiting for CONNECTED. */
    public Connection connectFromOrigin(Actor actor, String origin) {
        return connectFromOrigin(actor, origin, Map.of());
    }

    /** Opens the handshake with a browser Origin header and other handshake headers, without waiting. */
    public Connection connectFromOrigin(Actor actor, String origin, Map<String, String> handshakeHeaders) {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setOrigin(origin);
        handshakeHeaders.forEach(handshake::add);
        return open(actor, actor.bearer(), handshake);
    }

    private Connection open(Actor actor, String authorization) {
        return open(actor, authorization, new WebSocketHttpHeaders());
    }

    private Connection open(Actor actor, String authorization, WebSocketHttpHeaders handshake) {
        Connection connection = new Connection(actor);
        StompHeaders connectHeaders = new StompHeaders();
        if (authorization != null) {
            connectHeaders.add("Authorization", authorization);
        }
        client.connectAsync(url, handshake, connectHeaders, connection)
                .whenComplete((session, ex) -> {
                    if (ex != null) {
                        connection.closed.complete(null);
                    }
                });
        connections.add(connection);
        return connection;
    }

    @Override
    public void close() {
        connections.forEach(Connection::disconnect);
        client.stop();
        scheduler.shutdown();
    }

    public final class Connection implements StompSessionHandler {

        private final Actor actor;
        private final CompletableFuture<StompSession> connected = new CompletableFuture<>();
        private final CompletableFuture<Void> closed = new CompletableFuture<>();
        private final BlockingQueue<JsonNode> errorFrames = new LinkedBlockingQueue<>();
        private final Map<String, BlockingQueue<JsonNode>> received = new ConcurrentHashMap<>();
        private final List<Throwable> clientErrors = new CopyOnWriteArrayList<>();
        private StompSession session;

        private Connection(Actor actor) {
            this.actor = actor;
        }

        /** Subscribes and waits until the server has registered the subscription. */
        public void subscribe(String destination) {
            subscribeWithoutWaiting(destination);
            probe.awaitSubscribed(actor.id(), destination);
        }

        /** For subscriptions the server is expected to refuse. */
        public void subscribeWithoutWaiting(String destination) {
            BlockingQueue<JsonNode> queue = received.computeIfAbsent(destination, key -> new LinkedBlockingQueue<>());
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return JsonNode.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    queue.add((JsonNode) payload);
                }
            });
        }

        public void send(String destination, Object payload) {
            session.send(destination, payload);
        }

        /** The next message on {@code destination} matching {@code filter}; earlier non-matching ones are skipped. */
        public JsonNode next(String destination, Predicate<JsonNode> filter) throws InterruptedException {
            BlockingQueue<JsonNode> queue = received.get(destination);
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                JsonNode message = queue.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                if (message != null && filter.test(message)) {
                    return message;
                }
            }
            throw new AssertionError("No matching message on " + destination + " within " + TIMEOUT
                    + (clientErrors.isEmpty() ? "" : "; client errors: " + clientErrors));
        }

        public JsonNode next(String destination) throws InterruptedException {
            return next(destination, message -> true);
        }

        public void assertNothingReceived(String destination) throws InterruptedException {
            BlockingQueue<JsonNode> queue = received.get(destination);
            JsonNode unexpected = queue.poll(SILENCE.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(unexpected).as("unexpected message on " + destination).isNull();
        }

        /** The body of the ERROR frame the server sent before closing the connection. */
        public JsonNode errorFrame() throws InterruptedException {
            JsonNode error = errorFrames.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(error).as("ERROR frame").isNotNull();
            return error;
        }

        /** Waits for CONNECTED. */
        public void awaitConnected() throws Exception {
            session = connected.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }

        public void awaitClosed() throws Exception {
            closed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }

        public boolean isConnected() {
            return session != null && session.isConnected();
        }

        private void disconnect() {
            if (isConnected()) {
                session.disconnect();
            }
        }

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            connected.complete(session);
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload,
                                    Throwable exception) {
            clientErrors.add(exception);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            closed.complete(null);
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return JsonNode.class;
        }

        /** Only ERROR frames reach the session handler; subscription messages go to their frame handlers. */
        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errorFrames.add((JsonNode) payload);
        }
    }
}
