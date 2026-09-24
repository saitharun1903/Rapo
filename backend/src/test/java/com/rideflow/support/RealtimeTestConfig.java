package com.rideflow.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Registers the {@link SubscriptionProbe} on the client inbound channel. Frames the application rejects never
 * reach a handler, so they are never recorded.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RealtimeTestConfig {

    @Bean
    SubscriptionProbe subscriptionProbe() {
        return new SubscriptionProbe();
    }

    @Bean
    WebSocketMessageBrokerConfigurer subscriptionProbeRegistration(SubscriptionProbe probe) {
        return new WebSocketMessageBrokerConfigurer() {
            @Override
            public void configureClientInboundChannel(ChannelRegistration registration) {
                registration.interceptors(probe);
            }
        };
    }
}
