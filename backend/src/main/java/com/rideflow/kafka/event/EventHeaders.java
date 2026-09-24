package com.rideflow.kafka.event;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;

/**
 * Kafka record headers set on every event, so tools can route or filter without parsing the JSON. The trace
 * id travels in the envelope.
 */
public final class EventHeaders {

    public static final String EVENT_TYPE = "eventType";
    public static final String SCHEMA_VERSION = "schemaVersion";

    private EventHeaders() {
    }

    public static Headers of(String eventType) {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader(EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(SCHEMA_VERSION,
                Integer.toString(EventCodec.SCHEMA_VERSION).getBytes(StandardCharsets.UTF_8)));
        return headers;
    }
}
