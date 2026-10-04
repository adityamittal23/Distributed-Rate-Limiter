package com.distributed.ratelimiter.core.inmemory;

import com.distributed.ratelimiter.core.AlgorithmType;
import com.distributed.ratelimiter.core.Clock;
import com.distributed.ratelimiter.core.RateLimitResult;
import com.distributed.ratelimiter.core.RateLimiter;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe In-Memory Token Bucket Rate Limiter.
 * Uses lock-free AtomicReference CAS loops for atomic state transitions.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final long capacity;
    private final double refillTokensPerSecond;
    private final Clock clock;
    private final ConcurrentHashMap<String, AtomicReference<BucketState>> buckets = new ConcurrentHashMap<>();

    private record BucketState(double tokens, long lastRefillMs) {}

    public TokenBucketRateLimiter(long capacity, double refillTokensPerSecond, Clock clock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (refillTokensPerSecond <= 0) {
            throw new IllegalArgumentException("Refill rate must be positive");
        }
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    public TokenBucketRateLimiter(long capacity, double refillTokensPerSecond) {
        this(capacity, refillTokensPerSecond, Clock.systemUtc());
    }

    @Override
    public RateLimitResult tryAcquire(String key, long cost) {
        if (cost <= 0) {
            throw new IllegalArgumentException("Cost must be at least 1");
        }

        AtomicReference<BucketState> ref = buckets.computeIfAbsent(
                key,
                k -> new AtomicReference<>(new BucketState(capacity, clock.currentTimeMillis()))
        );

        while (true) {
            BucketState current = ref.get();
            long now = clock.currentTimeMillis();
            long elapsedMs = Math.max(0, now - current.lastRefillMs());
            double replenished = (elapsedMs / 1000.0) * refillTokensPerSecond;
            double tokens = Math.min((double) capacity, current.tokens() + replenished);

            if (tokens < cost) {
                // Not enough tokens available
                double deficit = cost - tokens;
                long waitMs = (long) Math.ceil((deficit / refillTokensPerSecond) * 1000.0);
                long resetAtMs = now + waitMs;
                return RateLimitResult.rejected((long) tokens, resetAtMs);
            }

            // Enough tokens, try CAS update
            double remainingTokens = tokens - cost;
            BucketState next = new BucketState(remainingTokens, now);
            if (ref.compareAndSet(current, next)) {
                double neededForFull = capacity - remainingTokens;
                long msToFull = (long) Math.ceil((neededForFull / refillTokensPerSecond) * 1000.0);
                long resetAtMs = now + msToFull;
                return RateLimitResult.allowed((long) remainingTokens, resetAtMs);
            }
        }
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.TOKEN_BUCKET;
    }

    @Override
    public void reset(String key) {
        buckets.remove(key);
    }

    public long getCapacity() {
        return capacity;
    }

    public double getRefillTokensPerSecond() {
        return refillTokensPerSecond;
    }
}
