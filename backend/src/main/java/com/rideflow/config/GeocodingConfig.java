package com.rideflow.config;

import com.rideflow.geospatial.DisabledGeocodingProvider;
import com.rideflow.geospatial.GeocodingProvider;
import com.rideflow.geospatial.NominatimGeocodingProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({GeocodingProperties.class, CacheProperties.class, RateLimitProperties.class})
public class GeocodingConfig {

    @Bean
    GeocodingProvider geocodingProvider(GeocodingProperties properties, RestClient.Builder restClientBuilder) {
        return switch (properties.provider()) {
            case NOMINATIM -> new NominatimGeocodingProvider(nominatimClient(properties, restClientBuilder),
                    properties.countryCodes(), properties.biasBoxDegrees());
            case DISABLED -> new DisabledGeocodingProvider();
        };
    }

    private static RestClient nominatimClient(GeocodingProperties properties, RestClient.Builder builder) {
        return OutboundHttp.client(builder, properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
                // Required by the Nominatim usage policy: generic library User-Agents are blocked.
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
    }
}
