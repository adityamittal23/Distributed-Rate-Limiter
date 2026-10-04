package com.distributed.ratelimiter.core;

/**
 * Common contract for rate limiting implementations.
 */
public interface RateLimiter {

    /**
     * Attempts to acquire the specified cost units for the given key.
     *
     * @param key  The identity key (e.g. user ID, API key, IP address, or composite).
     * @param cost The number of tokens or request units to consume (must be >= 1).
     * @return RateLimitResult indicating whether the acquisition succeeded and remaining quota.
     */
    RateLimitResult tryAcquire(String key, long cost);

    /**
     * Attempts to acquire 1 token/request unit for the given key.
     *
     * @param key The identity key.
     * @return RateLimitResult.
     */
    default RateLimitResult tryAcquire(String key) {
        return tryAcquire(key, 1L);
    }

    /**
     * Returns the algorithm type implemented by this rate limiter.
     */
    AlgorithmType getAlgorithmType();

    /**
     * Clears any stored state for the given key (primarily for testing and cache invalidation).
     */
    default void reset(String key) {
        // default no-op if unsupported
    }
}
