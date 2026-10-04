package com.distributed.ratelimiter.core;

import com.distributed.ratelimiter.core.inmemory.FixedWindowRateLimiter;
import com.distributed.ratelimiter.core.inmemory.SlidingWindowCounterRateLimiter;
import com.distributed.ratelimiter.core.inmemory.SlidingWindowLogRateLimiter;
import com.distributed.ratelimiter.core.inmemory.TokenBucketRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyStressTest {

    @Test
    @DisplayName("TokenBucket should strictly enforce limit under 50 concurrent threads")
    void testTokenBucketConcurrency() throws InterruptedException {
        int capacity = 50;
        // refill 1 token per hour so no refill occurs during the test
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(capacity, 1.0 / 3600.0);
        assertConcurrentLimit("tb-key", limiter, capacity, 100);
    }

    @Test
    @DisplayName("SlidingWindowCounter should strictly enforce limit under concurrent threads")
    void testSlidingWindowCounterConcurrency() throws InterruptedException {
        int limit = 50;
        SlidingWindowCounterRateLimiter limiter = new SlidingWindowCounterRateLimiter(limit, 60_000L);
        assertConcurrentLimit("swc-key", limiter, limit, 100);
    }

    @Test
    @DisplayName("SlidingWindowLog should strictly enforce limit under concurrent threads")
    void testSlidingWindowLogConcurrency() throws InterruptedException {
        int limit = 40;
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(limit, 60_000L);
        assertConcurrentLimit("swl-key", limiter, limit, 80);
    }

    @Test
    @DisplayName("FixedWindow should strictly enforce limit under concurrent threads")
    void testFixedWindowConcurrency() throws InterruptedException {
        int limit = 50;
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(limit, 60_000L);
        assertConcurrentLimit("fw-key", limiter, limit, 100);
    }

    private void assertConcurrentLimit(String key, RateLimiter limiter, int expectedAllowed, int totalRequests) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // wait for all threads to align
                    RateLimitResult result = limiter.tryAcquire(key);
                    if (result.allowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(allowedCount.get()).isEqualTo(expectedAllowed);
        assertThat(rejectedCount.get()).isEqualTo(totalRequests - expectedAllowed);
    }
}
