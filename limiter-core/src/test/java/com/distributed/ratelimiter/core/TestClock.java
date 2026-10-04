package com.distributed.ratelimiter.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Mutable clock for deterministic unit and concurrency testing.
 */
public class TestClock implements Clock {

    private final AtomicLong currentTime = new AtomicLong();

    public TestClock(long initialTimeMillis) {
        this.currentTime.set(initialTimeMillis);
    }

    public TestClock() {
        this(1_000_000_000L); // Arbitrary positive epoch timestamp
    }

    @Override
    public long currentTimeMillis() {
        return currentTime.get();
    }

    public void set(long millis) {
        currentTime.set(millis);
    }

    public void advanceMillis(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("Cannot go backwards in time");
        }
        currentTime.addAndGet(millis);
    }

    public void advanceSeconds(long seconds) {
        advanceMillis(seconds * 1000L);
    }
}
