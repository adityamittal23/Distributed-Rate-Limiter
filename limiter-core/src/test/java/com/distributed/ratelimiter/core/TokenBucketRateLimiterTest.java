package com.distributed.ratelimiter.core;

import com.distributed.ratelimiter.core.inmemory.TokenBucketRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBucketRateLimiterTest {

    private TestClock clock;

    @BeforeEach
    void setUp() {
        clock = new TestClock(10_000L);
    }

    @Test
    @DisplayName("Should allow burst requests up to bucket capacity")
    void testBurstCapacity() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 1.0, clock);

        for (int i = 0; i < 5; i++) {
            RateLimitResult result = limiter.tryAcquire("user-1");
            assertThat(result.allowed()).isTrue();
            assertThat(result.remaining()).isEqualTo(4 - i);
        }

        // 6th request must be rejected
        RateLimitResult blocked = limiter.tryAcquire("user-1");
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.remaining()).isEqualTo(0);
        assertThat(blocked.resetAtMs()).isGreaterThan(clock.currentTimeMillis());
    }

    @Test
    @DisplayName("Should refill tokens smoothly over elapsed time")
    void testTokenRefill() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 2.0, clock); // 2 tokens per second

        // Consume all 10 tokens
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("user-1").allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire("user-1").allowed()).isFalse();

        // Advance 1 second -> should replenish 2 tokens
        clock.advanceSeconds(1);
        assertThat(limiter.tryAcquire("user-1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-1").allowed()).isFalse();

        // Advance 5 seconds -> should replenish 10 tokens (capped at capacity 10)
        clock.advanceSeconds(5);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("user-1").allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire("user-1").allowed()).isFalse();
    }

    @Test
    @DisplayName("Should respect cost parameter when acquiring multiple tokens")
    void testCostParameter() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 1.0, clock);

        // Acquire 7 tokens
        RateLimitResult result1 = limiter.tryAcquire("user-1", 7);
        assertThat(result1.allowed()).isTrue();
        assertThat(result1.remaining()).isEqualTo(3);

        // Attempt to acquire 5 tokens with only 3 available -> rejected
        RateLimitResult result2 = limiter.tryAcquire("user-1", 5);
        assertThat(result2.allowed()).isFalse();

        // Acquire remaining 3 tokens -> allowed
        RateLimitResult result3 = limiter.tryAcquire("user-1", 3);
        assertThat(result3.allowed()).isTrue();
        assertThat(result3.remaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should isolate limits across different keys")
    void testKeyIsolation() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 1.0, clock);

        assertThat(limiter.tryAcquire("user-A").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-A").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-A").allowed()).isFalse();

        // user-B is unaffected
        assertThat(limiter.tryAcquire("user-B").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-B").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-B").allowed()).isFalse();
    }

    @Test
    @DisplayName("Should reject invalid configuration arguments")
    void testValidation() {
        assertThatThrownBy(() -> new TokenBucketRateLimiter(0, 1.0, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucketRateLimiter(5, 0.0, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
