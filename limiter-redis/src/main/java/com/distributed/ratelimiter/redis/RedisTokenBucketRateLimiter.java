package com.distributed.ratelimiter.redis;

import com.distributed.ratelimiter.core.AlgorithmType;
import com.distributed.ratelimiter.core.RateLimitResult;
import com.distributed.ratelimiter.core.RateLimiter;

/**
 * Distributed Token Bucket Rate Limiter backed by Redis and an atomic Lua script.
 */
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private static final String SCRIPT_PATH = "lua/token_bucket.lua";

    private final RedisLuaScriptExecutor scriptExecutor;
    private final String ruleId;
    private final long capacity;
    private final double refillTokensPerSecond;

    public RedisTokenBucketRateLimiter(
            RedisLuaScriptExecutor scriptExecutor,
            String ruleId,
            long capacity,
            double refillTokensPerSecond
    ) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (refillTokensPerSecond <= 0) {
            throw new IllegalArgumentException("Refill rate must be positive");
        }
        this.scriptExecutor = scriptExecutor;
        this.ruleId = ruleId;
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
    }

    @Override
    public RateLimitResult tryAcquire(String identity, long cost) {
        if (cost <= 0) {
            throw new IllegalArgumentException("Cost must be positive");
        }
        String key = RedisKeyFormatter.formatKey(ruleId, identity, AlgorithmType.TOKEN_BUCKET);
        String[] keys = new String[]{ key };
        String[] args = new String[]{
                String.valueOf(capacity),
                String.valueOf(refillTokensPerSecond),
                String.valueOf(cost)
        };
        return scriptExecutor.executeRateLimitScript(SCRIPT_PATH, keys, args);
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.TOKEN_BUCKET;
    }

    public String getRuleId() {
        return ruleId;
    }

    public long getCapacity() {
        return capacity;
    }

    public double getRefillTokensPerSecond() {
        return refillTokensPerSecond;
    }
}
