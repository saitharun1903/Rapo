package com.rideflow.kafka.event;

/**
 * A record that cannot be turned into a known event: not JSON, an unknown schema version, the wrong event
 * type for its topic, or a payload missing required fields. Retrying cannot help, so such records go
 * straight to the dead-letter topic.
 */
public class EventDecodingException extends RuntimeException {

    public EventDecodingException(String message) {
        super(message);
    }

    public EventDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
