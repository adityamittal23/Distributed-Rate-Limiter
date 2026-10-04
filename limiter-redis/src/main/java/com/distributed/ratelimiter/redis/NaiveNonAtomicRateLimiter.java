package com.distributed.ratelimiter.redis;

import com.distributed.ratelimiter.core.AlgorithmType;
import com.distributed.ratelimiter.core.RateLimitResult;
import com.distributed.ratelimiter.core.RateLimiter;
import io.lettuce.core.api.sync.RedisStringCommands;

/**
 * Deliberately non-atomic rate limiter implementation used for benchmarking
 * and demonstrating race conditions under concurrent load.
 *
 * Demonstrates the flaw of separate GET-then-SET operations across distributed nodes.
 */
public class NaiveNonAtomicRateLimiter implements RateLimiter {

    private final RedisStringCommands<String, String> stringCommands;
    private final String ruleId;
    private final long limit;

    public NaiveNonAtomicRateLimiter(RedisStringCommands<String, String> stringCommands, String ruleId, long limit) {
        this.stringCommands = stringCommands;
        this.ruleId = ruleId;
        this.limit = limit;
    }

    @Override
    public RateLimitResult tryAcquire(String identity, long cost) {
        String key = "naive:" + ruleId + ":" + identity;

        // Step 1: GET (RACE CONDITION WINDOW OPENS HERE)
        String val = stringCommands.get(key);
        long currentCount = (val != null) ? Long.parseLong(val) : 0L;

        // Deliberate tiny yield to simulate network latency between GET and SET
        Thread.yield();

        // Step 2: Check condition
        if (currentCount + cost <= limit) {
            // Step 3: SET (OVERWRITES OTHER CONCURRENT THREADS' WRITES)
            long newCount = currentCount + cost;
            stringCommands.set(key, String.valueOf(newCount));
            return RateLimitResult.allowed(limit - newCount, System.currentTimeMillis() + 60_000L);
        } else {
            return RateLimitResult.rejected(0, System.currentTimeMillis() + 60_000L);
        }
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.FIXED_WINDOW;
    }
}
