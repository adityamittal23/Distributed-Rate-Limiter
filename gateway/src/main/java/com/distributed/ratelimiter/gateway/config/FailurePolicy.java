package com.distributed.ratelimiter.gateway.config;

/**
 * Policy defining behavior when Redis is unavailable or times out.
 */
public enum FailurePolicy {
    /**
     * Permit traffic through while activating the in-memory fallback rate limiter
     * to prevent backend collapse during Redis downtime.
     */
    FAIL_OPEN,

    /**
     * Strictly reject traffic with HTTP 429 when Redis is down.
     * Recommended for security-critical routes such as authentication and logins.
     */
    FAIL_CLOSED
}
