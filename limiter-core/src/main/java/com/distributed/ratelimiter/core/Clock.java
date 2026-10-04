package com.distributed.ratelimiter.core;

/**
 * Clock abstraction to allow deterministic testing of time-dependent rate limiting algorithms.
 */
@FunctionalInterface
public interface Clock {

    /**
     * Returns the current time in milliseconds since the Unix epoch.
     */
    long currentTimeMillis();

    /**
     * Standard system UTC clock.
     */
    static Clock systemUtc() {
        return System::currentTimeMillis;
    }
}
