package com.rideflow.service.admin;

import com.rideflow.dto.admin.SystemStatusResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.stereotype.Service;

/**
 * Operational state for the admin console, read from the same health indicators and meters that Actuator and
 * Prometheus expose, so the console and the dashboards cannot disagree.
 */
@Service
public class SystemStatusService {

    private static final String OUTBOX_PENDING = "rideflow.outbox.pending";
    private static final String DEAD_LETTERS = "rideflow.kafka.dead.letters";
    private static final String AI_CIRCUIT_OPEN = "rideflow.ai.circuit.open";
    private static final String AI_CALLS_ACTIVE = "rideflow.ai.calls.active";
    private static final String WS_SESSIONS = "rideflow.ws.sessions.active";
    private static final String REDIS_AVAILABLE = "rideflow.redis.available";
    private static final String TOPIC_TAG = "topic";
    private static final String UNKNOWN = "UNKNOWN";

    private final MeterRegistry meters;
    private final ObjectProvider<HealthEndpoint> health;

    public SystemStatusService(MeterRegistry meters, ObjectProvider<HealthEndpoint> health) {
        this.meters = meters;
        this.health = health;
    }

    public SystemStatusResponse status() {
        HealthEndpoint endpoint = health.getIfAvailable();
        HealthDescriptor overall = endpoint == null ? null : endpoint.health();
        Map<String, String> components = new LinkedHashMap<>();
        if (overall instanceof CompositeHealthDescriptor composite) {
            composite.getComponents().forEach((name, component) ->
                    components.put(name, component.getStatus().getCode()));
        }
        Double aiCircuit = sum(AI_CIRCUIT_OPEN);
        Double redis = sum(REDIS_AVAILABLE);
        return new SystemStatusResponse(
                overall == null ? UNKNOWN : overall.getStatus().getCode(),
                components,
                whole(sum(OUTBOX_PENDING)),
                deadLettersByTopic(),
                aiCircuit == null ? null : aiCircuit > 0,
                whole(sum(AI_CALLS_ACTIVE)),
                whole(sum(WS_SESSIONS)),
                redis == null ? null : redis > 0);
    }

    /** Sum over every tag combination (sessions are counted per role); {@code null} if not registered. */
    private Double sum(String name) {
        Collection<Gauge> gauges = meters.find(name).gauges();
        return gauges.isEmpty() ? null : gauges.stream().mapToDouble(Gauge::value).sum();
    }

    private Map<String, Long> deadLettersByTopic() {
        Map<String, Long> byTopic = new TreeMap<>();
        for (Counter counter : meters.find(DEAD_LETTERS).counters()) {
            String topic = counter.getId().getTag(TOPIC_TAG);
            byTopic.merge(topic == null ? UNKNOWN : topic, Math.round(counter.count()), Long::sum);
        }
        return byTopic;
    }

    private static Long whole(Double value) {
        return value == null || value.isNaN() ? null : Math.round(value);
    }
}
