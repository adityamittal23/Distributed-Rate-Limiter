package com.distributed.ratelimiter.core;

import com.distributed.ratelimiter.core.inmemory.SlidingWindowCounterRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowCounterRateLimiterTest {

    private TestClock clock;

    @BeforeEach
    void setUp() {
        clock = new TestClock(60_000L); // Start at minute boundary
    }

    @Test
    @DisplayName("Should strictly respect limit within a single window")
    void testBasicLimit() {
        SlidingWindowCounterRateLimiter limiter = new SlidingWindowCounterRateLimiter(5, 60_000L, clock);

        for (int i = 0; i < 5; i++) {
            RateLimitResult result = limiter.tryAcquire("client-1");
            assertThat(result.allowed()).isTrue();
            assertThat(result.remaining()).isEqualTo(4 - i);
        }

        RateLimitResult blocked = limiter.tryAcquire("client-1");
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.remaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should weight previous window counts correctly across boundary")
    void testWindowBoundaryWeighting() {
        // Limit: 10 requests per 1000ms window
        SlidingWindowCounterRateLimiter limiter = new SlidingWindowCounterRateLimiter(10, 1000L, clock);

        // Window 1: Consume 10 requests at t=0
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("client-1").allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire("client-1").allowed()).isFalse();

        // Advance by 500ms into Window 2 (elapsed = 500ms, weight = (1 - 500/1000) = 0.5)
        // Estimated previous count: 10 * 0.5 = 5. Available = 10 - 5 = 5 requests.
        clock.advanceMillis(1500L); // now = 61500ms -> window 61, elapsed = 500ms
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("client-1").allowed()).isTrue();
        }

        // 6th request in this window would make estimatedCount: 5 (from prev) + 5 (current) + 1 = 11 > 10
        assertThat(limiter.tryAcquire("client-1").allowed()).isFalse();

        // Advance 2 full windows: previous window weight drops to 0
        clock.advanceMillis(2000L);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("client-1").allowed()).isTrue();
        }
    }
}
