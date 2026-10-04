package com.distributed.ratelimiter.redis;

import com.distributed.ratelimiter.core.AlgorithmType;

/**
 * Key formatter that ensures Redis Cluster compatibility using hash tags:
 * rl:{<identity>}:<algo>:<ruleId>
 * Wrapping the identity in curly braces ensures all keys for a single client map
 * to the exact same hash slot in a Redis Cluster shard topology.
 */
public final class RedisKeyFormatter {

    private static final String PREFIX = "rl";

    private RedisKeyFormatter() {}

    public static String formatKey(String ruleId, String identity, AlgorithmType algorithmType) {
        String safeIdentity = (identity == null || identity.isBlank()) ? "anonymous" : identity.trim();
        String safeRule = (ruleId == null || ruleId.isBlank()) ? "default" : ruleId.trim();
        return String.format("%s:{%s}:%s:%s", PREFIX, safeIdentity, algorithmType.getIdentifier(), safeRule);
    }
}
