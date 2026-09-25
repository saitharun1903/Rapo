package com.rideflow.config;

import com.rideflow.security.SignedClientIp;
import com.rideflow.security.SignedClientIpFilter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** The frontend-signed client address, active only when {@code CLIENT_IP_SIGNING_SECRET} is set. */
@Configuration(proxyBeanMethods = false)
@Conditional(ClientIpConfig.SigningSecretConfigured.class)
@EnableConfigurationProperties(ClientIpProperties.class)
public class ClientIpConfig {

    static final String SIGNING_SECRET_PROPERTY = "rideflow.security.client-ip.signing-secret";
    /** Same floor as the JWT key: an HMAC-SHA256 key below 256 bits is guessable offline. */
    static final int MIN_SECRET_BYTES = JwtConfig.MIN_SECRET_BYTES;
    /** After RequestIdFilter (so its log lines carry the trace id), before Spring Security and the controllers. */
    private static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

    @Bean
    SignedClientIp signedClientIp(ClientIpProperties properties, Clock clock) {
        byte[] secret = properties.signingSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "CLIENT_IP_SIGNING_SECRET must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        return new SignedClientIp(secret, properties.maxAge(), clock);
    }

    @Bean
    FilterRegistrationBean<SignedClientIpFilter> signedClientIpFilter(SignedClientIp signedClientIp,
                                                                      MeterRegistry meters) {
        FilterRegistrationBean<SignedClientIpFilter> registration =
                new FilterRegistrationBean<>(new SignedClientIpFilter(signedClientIp, meters));
        registration.setOrder(FILTER_ORDER);
        return registration;
    }

    /** An empty value (the default) means off; @ConditionalOnProperty would treat it as set. */
    static final class SigningSecretConfigured implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasLength(context.getEnvironment().getProperty(SIGNING_SECRET_PROPERTY));
        }
    }
}
