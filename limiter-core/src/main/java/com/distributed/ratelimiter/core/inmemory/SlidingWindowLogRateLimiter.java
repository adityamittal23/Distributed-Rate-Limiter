package com.distributed.ratelimiter.core.inmemory;

import com.distributed.ratelimiter.core.AlgorithmType;
import com.distributed.ratelimiter.core.Clock;
import com.distributed.ratelimiter.core.RateLimitResult;
import com.distributed.ratelimiter.core.RateLimiter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe In-Memory Sliding Window Log Rate Limiter.
 * Tracks precise timestamps of all accepted requests and trims timestamps older than (now - windowDurationMs).
 */
public class SlidingWindowLogRateLimiter implements RateLimiter {

    private final long limit;
    private final long windowDurationMs;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> logs = new ConcurrentHashMap<>();

    public SlidingWindowLogRateLimiter(long limit, long windowDurationMs, Clock clock) {
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

    public SlidingWindowLogRateLimiter(long limit, long windowDurationMs) {
        this(limit, windowDurationMs, Clock.systemUtc());
    }

    @Override
    public RateLimitResult tryAcquire(String key, long cost) {
        if (cost <= 0) {
            throw new IllegalArgumentException("Cost must be at least 1");
        }

        Deque<Long> queue = logs.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (queue) {
            long now = clock.currentTimeMillis();
            long threshold = now - windowDurationMs;

            // Evict expired entries
            while (!queue.isEmpty() && queue.peekFirst() <= threshold) {
                queue.pollFirst();
            }

            int currentCount = queue.size();
            long remaining = Math.max(0, limit - currentCount);

            if (currentCount + cost > limit) {
                // Determine when the oldest entry will expire to open quota
                long oldest = queue.isEmpty() ? now : queue.peekFirst();
                long resetAtMs = oldest + windowDurationMs;
                return RateLimitResult.rejected(remaining, resetAtMs);
            }

            // Grant access by recording timestamps
            for (int i = 0; i < cost; i++) {
                queue.addLast(now);
            }

            long newRemaining = Math.max(0, limit - (currentCount + cost));
            long oldest = queue.peekFirst();
            long resetAtMs = oldest + windowDurationMs;

            return RateLimitResult.allowed(newRemaining, resetAtMs);
        }
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.SLIDING_WINDOW_LOG;
    }

    @Override
    public void reset(String key) {
        logs.remove(key);
    }

    public long getLimit() {
        return limit;
    }

    public long getWindowDurationMs() {
        return windowDurationMs;
    }
}
