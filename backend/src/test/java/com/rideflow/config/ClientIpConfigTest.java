package com.rideflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideflow.security.SignedClientIpFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

class ClientIpConfigTest {

    private static final String SECRET = "rideflow.security.client-ip.signing-secret=";
    private static final String MAX_AGE = "rideflow.security.client-ip.max-age=60s";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClientIpConfig.class)
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues(MAX_AGE);

    @Test
    void withoutASecretNothingIsRegistered() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(FilterRegistrationBean.class));
        runner.withPropertyValues(SECRET).run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void aShortSecretStopsStartup() {
        runner.withPropertyValues(SECRET + "too-short").run(context -> assertThat(context).getFailure()
                .rootCause().hasMessageContaining("CLIENT_IP_SIGNING_SECRET must be at least 32 bytes"));
    }

    @Test
    void withASecretTheFilterRunsBeforeSecurityAndAfterTheRequestId() {
        runner.withPropertyValues(SECRET + "a-secret-long-enough-for-hmac-sha256-keys").run(context -> {
            FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(SignedClientIpFilter.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
        });
    }
}
