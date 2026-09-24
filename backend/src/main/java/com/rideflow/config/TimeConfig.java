package com.rideflow.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    /** Injected wherever "now" is needed so time-dependent logic is testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
