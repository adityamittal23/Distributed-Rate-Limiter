package com.distributed.ratelimiter.gateway.resilience;

import com.distributed.ratelimiter.core.RateLimitResult;
import com.distributed.ratelimiter.gateway.config.RuleDefinition;

/**
 * Service contract for evaluating rate limits for incoming requests.
 */
public interface RateLimiterEvaluationService {

    /**
     * Evaluates whether the request is permitted under the given rule, tier, and identity.
     *
     * @param rule     Matching rule definition.
     * @param tier     Client tier (e.g. "free", "pro").
     * @param identity Client identity key.
     * @param cost     Request cost (units).
     * @return RateLimitResult indicating allowance and remaining quota.
     */
    RateLimitResult evaluate(RuleDefinition rule, String tier, String identity, long cost);
}
