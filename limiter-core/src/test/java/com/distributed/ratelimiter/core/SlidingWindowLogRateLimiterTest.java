package com.distributed.ratelimiter.core;

import com.distributed.ratelimiter.core.inmemory.SlidingWindowLogRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowLogRateLimiterTest {

    private TestClock clock;

    @BeforeEach
    void setUp() {
        clock = new TestClock(100_000L);
    }

    @Test
    @DisplayName("Should strictly enforce sliding window log limit and evict past timestamps")
    void testSlidingWindowLog() {
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(3, 1000L, clock);

        // At t=0ms: 2 requests
        assertThat(limiter.tryAcquire("user-log").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-log").allowed()).isTrue();

        // At t=500ms: 1 request
        clock.advanceMillis(500L);
        assertThat(limiter.tryAcquire("user-log").allowed()).isTrue();

        // Limit reached: 4th request rejected
        assertThat(limiter.tryAcquire("user-log").allowed()).isFalse();

        // At t=1001ms: the first two requests (at t=0ms) have slid out of the 1000ms window!
        clock.advanceMillis(501L); // current time = 1000L + 1L = 1001L from start
        // Now only the request at t=500ms is in window, so 2 new requests should be allowed!
        assertThat(limiter.tryAcquire("user-log").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-log").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-log").allowed()).isFalse();
    }
}
