package com.distributed.ratelimiter.core;

/**
 * Result of a rate limiting decision.
 *
 * @param allowed     Whether the request is allowed or blocked.
 * @param remaining   Number of requests/tokens remaining in the current window.
 * @param resetAtMs   Epoch timestamp (in milliseconds) when the rate limit quota will fully or partially reset.
 */
public record RateLimitResult(
        boolean allowed,
        long remaining,
        long resetAtMs
) {
    public static RateLimitResult allowed(long remaining, long resetAtMs) {
        return new RateLimitResult(true, Math.max(0, remaining), resetAtMs);
    }

    public static RateLimitResult rejected(long remaining, long resetAtMs) {
        return new RateLimitResult(false, Math.max(0, remaining), resetAtMs);
    }

    /**
     * Computes the Retry-After interval in whole seconds relative to the current timestamp.
     */
    public long getRetryAfterSeconds(long currentEpochMs) {
        if (allowed) {
            return 0;
        }
        long deltaMs = resetAtMs - currentEpochMs;
        if (deltaMs <= 0) {
            return 1;
        }
        return (long) Math.ceil((double) deltaMs / 1000.0);
    }
}
