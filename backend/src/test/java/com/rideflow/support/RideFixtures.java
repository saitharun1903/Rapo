package com.rideflow.support;

import com.rideflow.entity.Driver;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.entity.Vehicle;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.UserRepository;
import com.rideflow.repository.VehicleRepository;
import com.rideflow.security.AccessTokenService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates ride-test actors directly in the database and resets ride state between tests. */
public class RideFixtures {

    private final UserRepository users;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final AccessTokenService accessTokens;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MutableClock clock;
    private final StringRedisTemplate redis;

    public RideFixtures(UserRepository users, DriverRepository drivers, VehicleRepository vehicles,
                        AccessTokenService accessTokens, JdbcTemplate jdbc, TransactionTemplate tx, MutableClock clock,
                        StringRedisTemplate redis) {
        this.users = users;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.accessTokens = accessTokens;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.redis = redis;
    }

    /** An authenticated test user: id plus a ready-to-use Authorization header value. */
    public record Actor(UUID id, String bearer) {
    }

    /**
     * Integration tests share one database and one Redis; ride tests need a clean slate of rides, online
     * drivers and cached values so other tests' state never leaks in.
     */
    public void reset() {
        clock.reset();
        jdbc.update("DELETE FROM rides");
        jdbc.update("DELETE FROM driver_locations");
        jdbc.update("UPDATE drivers SET availability = 'OFFLINE' WHERE availability <> 'OFFLINE'");
        // Cached surge, routes and ETAs from earlier tests must not leak into this one.
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    public Actor passenger() {
        User user = tx.execute(status -> users.save(newUser(Role.PASSENGER)));
        return actor(user);
    }

    public Actor admin() {
        User user = tx.execute(status -> users.save(newUser(Role.ADMIN)));
        return actor(user);
    }

    public Actor verifiedDriver(VehicleCategory category) {
        User user = tx.execute(status -> {
            User saved = users.save(newUser(Role.DRIVER));
            Driver driver = drivers.save(Driver.onboard(saved, "LIC" + shortId()));
            driver.verify(null, clock.instant());
            vehicles.save(Vehicle.register(driver, new Vehicle.VehicleDetails("Maruti Suzuki", "Dzire", "White",
                    "TS" + shortId(), (short) 2022, category, (short) 4)));
            return saved;
        });
        return actor(user);
    }

    private Actor actor(User user) {
        return new Actor(user.getId(), "Bearer " + accessTokens.issue(user).value());
    }

    private static User newUser(Role role) {
        String id = shortId();
        return User.register(role.name().toLowerCase(Locale.ROOT) + "-" + id + "@example.com", null,
                "{bcrypt}unused-in-ride-tests", "Test " + role + " " + id, role);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }
}
