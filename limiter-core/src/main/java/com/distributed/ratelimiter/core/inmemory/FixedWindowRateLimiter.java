package com.distributed.ratelimiter.core.inmemory;

import com.distributed.ratelimiter.core.AlgorithmType;
import com.distributed.ratelimiter.core.Clock;
import com.distributed.ratelimiter.core.RateLimitResult;
import com.distributed.ratelimiter.core.RateLimiter;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe In-Memory Fixed Window Rate Limiter.
 * Counts requests within discrete fixed-size time intervals.
 */
public class FixedWindowRateLimiter implements RateLimiter {

    private final long limit;
    private final long windowDurationMs;
    private final Clock clock;
    private final ConcurrentHashMap<String, AtomicReference<WindowBucket>> windows = new ConcurrentHashMap<>();

    private record WindowBucket(long windowStartMs, long count) {}

    public FixedWindowRateLimiter(long limit, long windowDurationMs, Clock clock) {
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

    public FixedWindowRateLimiter(long limit, long windowDurationMs) {
        this(limit, windowDurationMs, Clock.systemUtc());
    }

    @Override
    public RateLimitResult tryAcquire(String key, long cost) {
        if (cost <= 0) {
            throw new IllegalArgumentException("Cost must be at least 1");
        }

        AtomicReference<WindowBucket> ref = windows.computeIfAbsent(
                key,
                k -> {
                    long now = clock.currentTimeMillis();
                    long windowStart = (now / windowDurationMs) * windowDurationMs;
                    return new AtomicReference<>(new WindowBucket(windowStart, 0L));
                }
        );

        while (true) {
            WindowBucket current = ref.get();
            long now = clock.currentTimeMillis();
            long currentWindowStart = (now / windowDurationMs) * windowDurationMs;
            long resetAtMs = currentWindowStart + windowDurationMs;

            long currentCount = (current.windowStartMs() == currentWindowStart) ? current.count() : 0L;

            if (currentCount + cost > limit) {
                long remaining = Math.max(0, limit - currentCount);
                return RateLimitResult.rejected(remaining, resetAtMs);
            }

            WindowBucket next = new WindowBucket(currentWindowStart, currentCount + cost);
            if (ref.compareAndSet(current, next)) {
                long remaining = Math.max(0, limit - (currentCount + cost));
                return RateLimitResult.allowed(remaining, resetAtMs);
            }
        }
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.FIXED_WINDOW;
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
