package com.distributed.ratelimiter.core;

import com.distributed.ratelimiter.core.inmemory.FixedWindowRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    private TestClock clock;

    @BeforeEach
    void setUp() {
        clock = new TestClock(10_000L);
    }

    @Test
    @DisplayName("Should enforce limit in current fixed window and reset in next window")
    void testFixedWindowBoundary() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, 1000L, clock);

        assertThat(limiter.tryAcquire("fw-client").allowed()).isTrue();
        assertThat(limiter.tryAcquire("fw-client").allowed()).isTrue();
        assertThat(limiter.tryAcquire("fw-client").allowed()).isTrue();
        assertThat(limiter.tryAcquire("fw-client").allowed()).isFalse();

        // Advance to next window
        clock.advanceMillis(1000L);
        assertThat(limiter.tryAcquire("fw-client").allowed()).isTrue();
        assertThat(limiter.tryAcquire("fw-client").allowed()).isTrue();
    }
}
