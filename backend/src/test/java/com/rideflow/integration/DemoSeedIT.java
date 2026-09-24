package com.rideflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rideflow.support.IntegrationTestContainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The demo profile loads seed accounts whose pgcrypto-generated hashes work with the application's encoder. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = "DEMO_USER_PASSWORD=" + DemoSeedIT.DEMO_PASSWORD)
class DemoSeedIT extends IntegrationTestContainers {

    static final String DEMO_PASSWORD = "Demo-seed-pass-7";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seededAdminCanSignIn() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@rideflow.example.com\",\"password\":\"%s\"}".formatted(DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void seedContainsAccountsButNoTripData() {
        // The container is shared with other integration tests, so only count the seed's own accounts.
        Integer verifiedSeedDrivers = jdbc.queryForObject("""
                SELECT count(*) FROM drivers d JOIN users u ON u.id = d.id
                WHERE u.email LIKE '%@rideflow.example.com' AND d.verification_status = 'VERIFIED'
                """, Integer.class);
        Integer seedDriverLocations = jdbc.queryForObject("""
                SELECT count(*) FROM driver_locations l JOIN users u ON u.id = l.driver_id
                WHERE u.email LIKE '%@rideflow.example.com'
                """, Integer.class);

        assertThat(verifiedSeedDrivers).isEqualTo(5);
        assertThat(seedDriverLocations).isZero();
    }
}
