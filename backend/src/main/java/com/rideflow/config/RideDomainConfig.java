package com.rideflow.config;

import com.rideflow.geospatial.OsrmRoutingProvider;
import com.rideflow.geospatial.RoutingProvider;
import com.rideflow.geospatial.RoutingService;
import com.rideflow.geospatial.StraightLineRoutingProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties({
    PricingProperties.class,
    SurgeProperties.class,
    MatchingProperties.class,
    RideProperties.class,
    RoutingProperties.class,
    ReportingProperties.class,
    ChatProperties.class
})
public class RideDomainConfig {

    private static final String USER_AGENT = "RideFlow/0.1 (+https://github.com/saitharun1903/Rapo)";

    @Bean
    RoutingService routingService(RoutingProperties properties, RestClient.Builder restClientBuilder) {
        StraightLineRoutingProvider fallback = new StraightLineRoutingProvider(properties);
        RoutingProvider primary = switch (properties.provider()) {
            case OSRM -> new OsrmRoutingProvider(osrmClient(properties, restClientBuilder));
            case STRAIGHT_LINE -> fallback;
        };
        return new RoutingService(primary, fallback);
    }

    private static RestClient osrmClient(RoutingProperties properties, RestClient.Builder builder) {
        return OutboundHttp.client(builder, properties.osrmBaseUrl(), properties.connectTimeout(), properties.readTimeout())
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }
}
