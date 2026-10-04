package com.distributed.ratelimiter.gateway.resilience;

import com.distributed.ratelimiter.core.Clock;
import com.distributed.ratelimiter.core.RateLimitResult;
import com.distributed.ratelimiter.core.RateLimiter;
import com.distributed.ratelimiter.core.inmemory.InMemoryRateLimiterFactory;
import com.distributed.ratelimiter.gateway.config.FailurePolicy;
import com.distributed.ratelimiter.gateway.config.RuleDefinition;
import com.distributed.ratelimiter.gateway.config.TierLimit;
import com.distributed.ratelimiter.gateway.metrics.RateLimiterMetrics;
import com.distributed.ratelimiter.redis.RedisLuaScriptExecutor;
import com.distributed.ratelimiter.redis.RedisRateLimiterFactory;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resilient Rate Limiter Service wrapping Redis Lua calls with Resilience4j CircuitBreaker,
 * timeouts, and graceful local in-memory degradation policies (FAIL_OPEN vs FAIL_CLOSED).
 */
@Service
public class ResilientRateLimiterService implements RateLimiterEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ResilientRateLimiterService.class);

    private final RedisLuaScriptExecutor scriptExecutor;
    private final RateLimiterMetrics metrics;
    private final CircuitBreaker circuitBreaker;

    // Cache of distributed Redis rate limiters per (ruleId:tier)
    private final Map<String, RateLimiter> redisLimiters = new ConcurrentHashMap<>();

    // In-memory fallback rate limiters per (ruleId:tier)
    private final Map<String, RateLimiter> fallbackLimiters = new ConcurrentHashMap<>();

    @Autowired
    public ResilientRateLimiterService(
            @Autowired(required = false) RedisLuaScriptExecutor scriptExecutor,
            RateLimiterMetrics metrics
    ) {
        this.scriptExecutor = scriptExecutor;
        this.metrics = metrics;

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofMillis(50))
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .slidingWindowSize(20)
                .minimumNumberOfCalls(5)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(cbConfig);
        this.circuitBreaker = registry.circuitBreaker("redisRateLimiter");
    }

    /**
     * Evaluates rate limiting decision with circuit breaker protection and fallback.
     */
    public RateLimitResult evaluate(
            RuleDefinition rule,
            String tier,
            String identity,
            long cost
    ) {
        String limiterKey = String.format("%s:%s", rule.getId(), tier);
        TierLimit tierLimit = rule.resolveTier(tier);
        long startNanos = System.nanoTime();

        // If Redis script executor is unavailable (e.g. standalone test mode or Redis not configured)
        if (scriptExecutor == null) {
            return executeFallback(rule, tier, identity, cost, "redis_not_configured");
        }

        try {
            // Execute Redis call through Circuit Breaker
            RateLimiter limiter = redisLimiters.computeIfAbsent(limiterKey, k ->
                    RedisRateLimiterFactory.create(
                            scriptExecutor,
                            rule.getAlgorithm(),
                            rule.getId(),
                            tierLimit.getCapacity(),
                            tierLimit.getRefillPerSecond()
                    )
            );

            RateLimitResult result = circuitBreaker.executeSupplier(() -> limiter.tryAcquire(identity, cost));
            metrics.recordDecisionLatency(rule.getAlgorithm().getIdentifier(), System.nanoTime() - startNanos);
            return result;

        } catch (CallNotPermittedException e) {
            log.warn("Redis Circuit Breaker is OPEN. Triggering fallback for rule [{}]", rule.getId());
            metrics.recordRedisError("circuit_breaker_open");
            return executeFallback(rule, tier, identity, cost, "circuit_breaker_open");
        } catch (Exception e) {
            log.error("Redis call failed for rule [{}]: {}. Triggering fallback...", rule.getId(), e.getMessage());
            metrics.recordRedisError(e.getClass().getSimpleName());
            return executeFallback(rule, tier, identity, cost, "redis_exception");
        }
    }

    private RateLimitResult executeFallback(
            RuleDefinition rule,
            String tier,
            String identity,
            long cost,
            String failureReason
    ) {
        FailurePolicy policy = (rule.getFailurePolicy() != null) ? rule.getFailurePolicy() : FailurePolicy.FAIL_OPEN;

        if (policy == FailurePolicy.FAIL_CLOSED) {
            log.warn("Policy is FAIL_CLOSED for rule [{}]. Rejecting request.", rule.getId());
            metrics.recordFallback(rule.getId(), "rejected_fail_closed");
            return RateLimitResult.rejected(0, System.currentTimeMillis() + 5000L);
        }

        // FAIL_OPEN with local in-memory fallback limiter to prevent backend collapse!
        String limiterKey = String.format("%s:%s", rule.getId(), tier);
        TierLimit tierLimit = rule.resolveTier(tier);

        RateLimiter inMemoryFallback = fallbackLimiters.computeIfAbsent(limiterKey, k ->
                InMemoryRateLimiterFactory.create(
                        rule.getAlgorithm(),
                        tierLimit.getCapacity(),
                        tierLimit.getRefillPerSecond(),
                        Clock.systemUtc()
                )
        );

        RateLimitResult fallbackResult = inMemoryFallback.tryAcquire(identity, cost);
        metrics.recordFallback(rule.getId(), fallbackResult.allowed() ? "allowed_in_memory" : "rejected_in_memory");
        return fallbackResult;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
