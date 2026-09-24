package com.rideflow.config;

import com.rideflow.websocket.StompAuthenticationInterceptor;
import com.rideflow.websocket.StompAuthorizationInterceptor;
import com.rideflow.websocket.StompDestinations;
import com.rideflow.websocket.StompErrorFrameHandler;
import com.rideflow.websocket.WebSocketSessionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * STOMP over native WebSocket at {@code /ws} with Spring's in-memory broker. Every instance pushes only
 * to the clients connected to it; Phase 6 feeds each instance from Kafka so that remains correct with
 * several instances. Contract: docs/events.md section 2.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
@EnableConfigurationProperties({RealtimeProperties.class, CorsProperties.class})
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthenticationInterceptor authentication;
    private final StompAuthorizationInterceptor authorization;
    private final StompErrorFrameHandler errorHandler;
    private final WebSocketSessionRegistry sessionRegistry;
    private final RealtimeProperties properties;
    private final CorsProperties cors;
    private TaskScheduler brokerScheduler;

    public WebSocketConfig(StompAuthenticationInterceptor authentication, StompAuthorizationInterceptor authorization,
                           StompErrorFrameHandler errorHandler, WebSocketSessionRegistry sessionRegistry,
                           RealtimeProperties properties, CorsProperties cors) {
        this.authentication = authentication;
        this.authorization = authorization;
        this.errorHandler = errorHandler;
        this.sessionRegistry = sessionRegistry;
        this.properties = properties;
        this.cors = cors;
    }

    /** The broker's own scheduler (defined by the configuration this class customises, hence lazy). */
    @Autowired
    void setBrokerScheduler(@Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler brokerScheduler) {
        this.brokerScheduler = brokerScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Browsers send Origin on the handshake; only the configured frontends may open a socket.
        registry.addEndpoint(StompDestinations.ENDPOINT)
                .setAllowedOrigins(cors.allowedOrigins().toArray(String[]::new));
        registry.setErrorHandler(errorHandler);
        // Process each client's frames in order (SUBSCRIBE before the SEND that follows it).
        registry.setPreserveReceiveOrder(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        long heartbeatMillis = properties.heartbeat().toMillis();
        registry.enableSimpleBroker(StompDestinations.TOPIC_PREFIX, StompDestinations.QUEUE_PREFIX)
                .setHeartbeatValue(new long[] {heartbeatMillis, heartbeatMillis})
                .setTaskScheduler(brokerScheduler);
        registry.setApplicationDestinationPrefixes(StompDestinations.APP_PREFIX);
        registry.setUserDestinationPrefix(StompDestinations.USER_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Order matters: authenticate (CONNECT, token expiry) before authorising destinations.
        registration.interceptors(authentication, authorization);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(sessionRegistry);
    }
}
