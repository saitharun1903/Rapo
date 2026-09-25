package com.rideflow.cache;

/** What is being limited. Limits per scope live in {@code rideflow.rate-limit.rules}. */
public enum RateLimitScope {
    /** Per client IP and email: slows password guessing against one account. */
    LOGIN,
    /** Per client IP: slows mass account creation. */
    REGISTER,
    /** Per passenger. */
    RIDE_BOOKING,
    /** Per passenger: each estimate costs a routing call and two PostGIS counts. */
    FARE_ESTIMATE,
    /** Per user: protects the geocoding provider's quota. */
    GEOCODING,
    /** Global: Nominatim's usage policy allows at most one request per second from the whole application. */
    GEOCODING_UPSTREAM,
    /** Per user: a route preview that misses the cache is a call to the public router. */
    ROUTE,
    /** Per passenger: every trip question is a model call, which costs money (external) or CPU time (local). */
    AI_QUESTION,
    /** Per passenger: re-running a failed trip analysis. */
    AI_REGENERATE
}
