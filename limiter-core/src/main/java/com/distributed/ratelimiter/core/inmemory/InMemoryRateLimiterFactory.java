package com.distributed.ratelimiter.core.inmemory;

import com.distributed.ratelimiter.core.AlgorithmType;
import com.distributed.ratelimiter.core.Clock;
import com.distributed.ratelimiter.core.RateLimiter;

/**
 * Factory for creating in-memory RateLimiter instances.
 */
public final class InMemoryRateLimiterFactory {

    private InMemoryRateLimiterFactory() {}

    public static RateLimiter createTokenBucket(long capacity, double refillPerSecond, Clock clock) {
        return new TokenBucketRateLimiter(capacity, refillPerSecond, clock);
    }

    public static RateLimiter createSlidingWindowCounter(long limit, long windowDurationMs, Clock clock) {
        return new SlidingWindowCounterRateLimiter(limit, windowDurationMs, clock);
    }

    public static RateLimiter createSlidingWindowLog(long limit, long windowDurationMs, Clock clock) {
        return new SlidingWindowLogRateLimiter(limit, windowDurationMs, clock);
    }

    public static RateLimiter createFixedWindow(long limit, long windowDurationMs, Clock clock) {
        return new FixedWindowRateLimiter(limit, windowDurationMs, clock);
    }

    public static RateLimiter create(AlgorithmType type, long capacityOrLimit, double refillOrWindowSeconds, Clock clock) {
        return switch (type) {
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(capacityOrLimit, refillOrWindowSeconds, clock);
            case SLIDING_WINDOW_COUNTER -> new SlidingWindowCounterRateLimiter(capacityOrLimit, (long) (refillOrWindowSeconds * 1000L), clock);
            case SLIDING_WINDOW_LOG -> new SlidingWindowLogRateLimiter(capacityOrLimit, (long) (refillOrWindowSeconds * 1000L), clock);
            case FIXED_WINDOW -> new FixedWindowRateLimiter(capacityOrLimit, (long) (refillOrWindowSeconds * 1000L), clock);
        };
    }
}
