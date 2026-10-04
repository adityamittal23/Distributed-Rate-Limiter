package com.distributed.ratelimiter.core.inmemory;

import com.distributed.ratelimiter.core.AlgorithmType;
import com.distributed.ratelimiter.core.Clock;
import com.distributed.ratelimiter.core.RateLimitResult;
import com.distributed.ratelimiter.core.RateLimiter;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe In-Memory Sliding Window Counter Rate Limiter.
 * Approximates request frequency over a moving window using the formula:
 * count = (previousWindowCount * (1 - elapsedInCurrentWindow / windowDuration)) + currentWindowCount
 */
public class SlidingWindowCounterRateLimiter implements RateLimiter {

    private final long limit;
    private final long windowDurationMs;
    private final Clock clock;
    private final ConcurrentHashMap<String, AtomicReference<WindowState>> windows = new ConcurrentHashMap<>();

    private record WindowState(long windowIndex, long currentCount, long previousCount) {}

    public SlidingWindowCounterRateLimiter(long limit, long windowDurationMs, Clock clock) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        if (windowDurationMs <= 0) {
            throw new IllegalArgumentException("Window duration must be positive");
        }
        this.limit = limit;
        this.windowDurationMs = windowDurationMs;
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    public SlidingWindowCounterRateLimiter(long limit, long windowDurationMs) {
        this(limit, windowDurationMs, Clock.systemUtc());
    }

    @Override
    public RateLimitResult tryAcquire(String key, long cost) {
        if (cost <= 0) {
            throw new IllegalArgumentException("Cost must be at least 1");
        }

        AtomicReference<WindowState> ref = windows.computeIfAbsent(
                key,
                k -> {
                    long now = clock.currentTimeMillis();
                    long windowIndex = now / windowDurationMs;
                    return new AtomicReference<>(new WindowState(windowIndex, 0L, 0L));
                }
        );

        while (true) {
            WindowState current = ref.get();
            long now = clock.currentTimeMillis();
            long currentIndex = now / windowDurationMs;
            long resetAtMs = (currentIndex + 1) * windowDurationMs;

            long prevCount;
            long curCount;

            if (currentIndex == current.windowIndex()) {
                prevCount = current.previousCount();
                curCount = current.currentCount();
            } else if (currentIndex == current.windowIndex() + 1) {
                prevCount = current.currentCount();
                curCount = 0L;
            } else {
                prevCount = 0L;
                curCount = 0L;
            }

            long elapsedInWindow = now % windowDurationMs;
            double weight = Math.max(0.0, 1.0 - ((double) elapsedInWindow / windowDurationMs));
            double estimatedCount = (prevCount * weight) + curCount;

            if (estimatedCount + cost > limit) {
                long remaining = Math.max(0, limit - (long) Math.floor(estimatedCount));
                return RateLimitResult.rejected(remaining, resetAtMs);
            }

            // Allowed, update state
            WindowState next = new WindowState(currentIndex, curCount + cost, prevCount);
            if (ref.compareAndSet(current, next)) {
                long remaining = Math.max(0, limit - (long) Math.floor(estimatedCount + cost));
                return RateLimitResult.allowed(remaining, resetAtMs);
            }
        }
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.SLIDING_WINDOW_COUNTER;
    }

    @Override
    public void reset(String key) {
        windows.remove(key);
    }

    public long getLimit() {
        return limit;
    }

    public long getWindowDurationMs() {
        return windowDurationMs;
    }
}
