package com.distributed.ratelimiter.redis;

import com.distributed.ratelimiter.core.AlgorithmType;
import com.distributed.ratelimiter.core.RateLimitResult;
import com.distributed.ratelimiter.core.RateLimiter;

/**
 * Distributed Sliding Window Log Rate Limiter backed by Redis Sorted Sets and an atomic Lua script.
 */
public class RedisSlidingWindowLogRateLimiter implements RateLimiter {

    private static final String SCRIPT_PATH = "lua/sliding_window_log.lua";

    private final RedisLuaScriptExecutor scriptExecutor;
    private final String ruleId;
    private final long limit;
    private final long windowDurationMs;

    public RedisSlidingWindowLogRateLimiter(
            RedisLuaScriptExecutor scriptExecutor,
            String ruleId,
            long limit,
            long windowDurationMs
    ) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        if (windowDurationMs <= 0) {
            throw new IllegalArgumentException("Window duration must be positive");
        }
        this.scriptExecutor = scriptExecutor;
        this.ruleId = ruleId;
        this.limit = limit;
        this.windowDurationMs = windowDurationMs;
    }

    @Override
    public RateLimitResult tryAcquire(String identity, long cost) {
        if (cost <= 0) {
            throw new IllegalArgumentException("Cost must be positive");
        }
        String key = RedisKeyFormatter.formatKey(ruleId, identity, AlgorithmType.SLIDING_WINDOW_LOG);
        String[] keys = new String[]{ key };
        String[] args = new String[]{
                String.valueOf(limit),
                String.valueOf(windowDurationMs),
                String.valueOf(cost)
        };
        return scriptExecutor.executeRateLimitScript(SCRIPT_PATH, keys, args);
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.SLIDING_WINDOW_LOG;
    }

    public String getRuleId() {
        return ruleId;
    }

    public long getLimit() {
        return limit;
    }

    public long getWindowDurationMs() {
        return windowDurationMs;
    }
}
