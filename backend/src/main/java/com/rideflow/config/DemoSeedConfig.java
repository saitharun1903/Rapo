package com.rideflow.config;

import com.rideflow.dto.validation.StrongPasswordValidator;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * The demo profile seeds accounts, an admin among them, with DEMO_USER_PASSWORD (application-demo.yml). The
 * backend refuses to start with a missing, empty or weak one: an empty value would otherwise be hashed and
 * accepted, and a deployment running the demo profile would have an admin anyone could guess.
 */
@Configuration(proxyBeanMethods = false)
@Profile("demo")
public class DemoSeedConfig {

    static final String PASSWORD = "DEMO_USER_PASSWORD";

    /** Before any bean is created, so before Flyway runs the seed. */
    @Bean
    static BeanFactoryPostProcessor demoPasswordCheck(Environment environment) {
        return beanFactory -> requireStrongPassword(environment);
    }

    static void requireStrongPassword(Environment environment) {
        String password = environment.getProperty(PASSWORD);
        if (!StringUtils.hasText(password) || !new StrongPasswordValidator().isValid(password, null)) {
            throw new IllegalStateException(PASSWORD + " must be 10-72 bytes with at least one letter and one digit: "
                    + "it is the password of every seeded account, the demo admin included");
        }
    }
}
