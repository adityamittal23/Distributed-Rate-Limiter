package com.distributed.ratelimiter.redis;

import com.distributed.ratelimiter.core.AlgorithmType;
import com.distributed.ratelimiter.core.RateLimiter;

/**
 * Factory for creating distributed Redis-backed RateLimiter instances.
 */
public final class RedisRateLimiterFactory {

    private RedisRateLimiterFactory() {}

    public static RateLimiter create(
            RedisLuaScriptExecutor scriptExecutor,
            AlgorithmType algorithmType,
            String ruleId,
            long capacityOrLimit,
            double refillOrWindowSeconds
    ) {
        return switch (algorithmType) {
            case TOKEN_BUCKET -> new RedisTokenBucketRateLimiter(
                    scriptExecutor,
                    ruleId,
                    capacityOrLimit,
                    refillOrWindowSeconds
            );
            case SLIDING_WINDOW_COUNTER -> new RedisSlidingWindowCounterRateLimiter(
                    scriptExecutor,
                    ruleId,
                    capacityOrLimit,
                    (long) (refillOrWindowSeconds * 1000.0)
            );
            case SLIDING_WINDOW_LOG -> new RedisSlidingWindowLogRateLimiter(
                    scriptExecutor,
                    ruleId,
                    capacityOrLimit,
                    (long) (refillOrWindowSeconds * 1000.0)
            );
            case FIXED_WINDOW -> new RedisFixedWindowRateLimiter(
                    scriptExecutor,
                    ruleId,
                    capacityOrLimit,
                    (long) (refillOrWindowSeconds * 1000.0)
            );
        };
    }
}
