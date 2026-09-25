package com.rideflow.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class StartupChecksTest {

    @Test
    void theProdProfileNamesEveryMissingInfrastructureSetting() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "jdbc:postgresql://db.example/rideflow?sslmode=require")
                .withProperty("DATABASE_USERNAME", "rideflow")
                .withProperty("REDIS_HOST", " ");

        assertThatThrownBy(() -> ProductionSettingsConfig.requireAll(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIS_HOST, KAFKA_BOOTSTRAP_SERVERS, CORS_ALLOWED_ORIGINS")
                .hasMessageNotContaining("DATABASE_URL");
    }

    @Test
    void theProdProfileStartsWhenEverySettingIsGiven() {
        MockEnvironment environment = new MockEnvironment();
        ProductionSettingsConfig.REQUIRED.forEach(name -> environment.setProperty(name, "set"));

        assertThatCode(() -> ProductionSettingsConfig.requireAll(environment)).doesNotThrowAnyException();
    }

    @Test
    void theDemoProfileRefusesAMissingEmptyOrWeakSeedPassword() {
        for (String password : new String[] {null, "", "short1", "no-digits-at-all"}) {
            MockEnvironment environment = new MockEnvironment();
            if (password != null) {
                environment.setProperty(DemoSeedConfig.PASSWORD, password);
            }
            assertThatThrownBy(() -> DemoSeedConfig.requireStrongPassword(environment))
                    .as("password %s", password)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DEMO_USER_PASSWORD");
        }
    }

    @Test
    void theDemoProfileAcceptsAStrongSeedPassword() {
        MockEnvironment environment = new MockEnvironment().withProperty(DemoSeedConfig.PASSWORD, "Demo-seed-pass-7");

        assertThatCode(() -> DemoSeedConfig.requireStrongPassword(environment)).doesNotThrowAnyException();
    }
}
