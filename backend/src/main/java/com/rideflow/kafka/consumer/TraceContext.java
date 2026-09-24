package com.rideflow.kafka.consumer;

import com.rideflow.monitoring.RequestIdFilter;
import org.slf4j.MDC;

/** Runs a handler with the producing request's trace id in the log context, so its logs can be correlated. */
final class TraceContext {

    private TraceContext() {
    }

    static void run(String traceId, Runnable handler) {
        if (traceId == null) {
            handler.run();
            return;
        }
        MDC.put(RequestIdFilter.TRACE_ID_MDC_KEY, traceId);
        try {
            handler.run();
        } finally {
            MDC.remove(RequestIdFilter.TRACE_ID_MDC_KEY);
        }
    }
}
