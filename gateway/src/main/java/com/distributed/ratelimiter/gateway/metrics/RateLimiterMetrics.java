package com.distributed.ratelimiter.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer metrics instrumentation for the Rate Limiter Gateway.
 */
@Component
public class RateLimiterMetrics {

    private final MeterRegistry meterRegistry;

    private final ConcurrentMap<String, Counter> allowedCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> blockedCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> redisErrorCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> fallbackCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> latencyTimers = new ConcurrentHashMap<>();

    public RateLimiterMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // Pre-initialize counters so Prometheus scrapes them even during healthy operation
        this.redisErrorCounters.computeIfAbsent("none", k -> Counter.builder("rl_redis_errors_total")
                .description("Total number of Redis errors or timeouts")
                .tag("type", "none")
                .register(meterRegistry));
        this.fallbackCounters.computeIfAbsent("api-default:none", k -> Counter.builder("rl_fallback_total")
                .description("Total number of fallback activations during Redis outages")
                .tag("rule", "api-default")
                .tag("outcome", "none")
                .register(meterRegistry));
    }

    public void recordAllowed(String rule, String tier, String algorithm) {
        String key = String.format("%s:%s:%s", rule, tier, algorithm);
        allowedCounters.computeIfAbsent(key, k -> Counter.builder("rl_allowed_total")
                .description("Total number of allowed requests")
                .tag("rule", rule)
                .tag("tier", tier)
                .tag("algorithm", algorithm)
                .register(meterRegistry)
        ).increment();
    }

    public void recordBlocked(String rule, String tier, String algorithm, String reason) {
        String key = String.format("%s:%s:%s:%s", rule, tier, algorithm, reason);
        blockedCounters.computeIfAbsent(key, k -> Counter.builder("rl_blocked_total")
                .description("Total number of blocked (HTTP 429) requests")
                .tag("rule", rule)
                .tag("tier", tier)
                .tag("algorithm", algorithm)
                .tag("reason", reason)
                .register(meterRegistry)
        ).increment();
    }

    public void recordRedisError(String errorType) {
        redisErrorCounters.computeIfAbsent(errorType, k -> Counter.builder("rl_redis_errors_total")
                .description("Total number of Redis errors or timeouts")
                .tag("type", errorType)
                .register(meterRegistry)
        ).increment();
    }

    public void recordFallback(String rule, String outcome) {
        String key = String.format("%s:%s", rule, outcome);
        fallbackCounters.computeIfAbsent(key, k -> Counter.builder("rl_fallback_total")
                .description("Total number of fallback activations during Redis outages")
                .tag("rule", rule)
                .tag("outcome", outcome)
                .register(meterRegistry)
        ).increment();
    }

    public void recordDecisionLatency(String algorithm, long durationNanos) {
        latencyTimers.computeIfAbsent(algorithm, k -> Timer.builder("rl_decision_latency")
                .description("Rate limit evaluation decision latency")
                .tag("algorithm", algorithm)
                .publishPercentileHistogram(true)
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry)
        ).record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
