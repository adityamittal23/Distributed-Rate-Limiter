package com.distributed.ratelimiter.gateway.filter;

import com.distributed.ratelimiter.core.RateLimitResult;
import com.distributed.ratelimiter.gateway.cache.HotKeyCache;
import com.distributed.ratelimiter.gateway.config.RuleDefinition;
import com.distributed.ratelimiter.gateway.config.TierLimit;
import com.distributed.ratelimiter.gateway.extractor.CompositeKeyExtractor;
import com.distributed.ratelimiter.gateway.matcher.RuleMatcher;
import com.distributed.ratelimiter.gateway.metrics.RateLimiterMetrics;
import com.distributed.ratelimiter.gateway.resilience.RateLimiterEvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Main Rate Limiting Filter enforcing per-identity quotas, injecting RFC headers,
 * and rejecting abusive traffic with HTTP 429 Too Many Requests.
 */
public class RateLimitFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    public static final String HEADER_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RESET = "X-RateLimit-Reset";
    public static final String HEADER_RETRY_AFTER = "Retry-After";
    public static final String HEADER_USER_TIER = "X-User-Tier";

    private final RuleMatcher ruleMatcher;
    private final CompositeKeyExtractor keyExtractor;
    private final HotKeyCache hotKeyCache;
    private final RateLimiterEvaluationService limiterService;
    private final RateLimiterMetrics metrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(
            RuleMatcher ruleMatcher,
            CompositeKeyExtractor keyExtractor,
            HotKeyCache hotKeyCache,
            RateLimiterEvaluationService limiterService,
            RateLimiterMetrics metrics
    ) {
        this.ruleMatcher = ruleMatcher;
        this.keyExtractor = keyExtractor;
        this.hotKeyCache = hotKeyCache;
        this.limiterService = limiterService;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // 1. Bypass actuator and internal diagnostic endpoints
        if (path.startsWith("/actuator") || path.startsWith("/favicon.ico")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Resolve matching rule
        Optional<RuleDefinition> matchingRuleOpt = ruleMatcher.findMatchingRule(path, method);
        if (matchingRuleOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        RuleDefinition rule = matchingRuleOpt.get();
        String identity = keyExtractor.extract(request, rule.getKey());
        String tier = resolveTier(request);
        TierLimit tierLimit = rule.resolveTier(tier);

        String compositeCacheKey = String.format("%s:%s:%s", rule.getId(), tier, identity);
        long now = System.currentTimeMillis();

        // 3. Fast-drop shielded check: HotKeyCache (L1)
        if (hotKeyCache.isBlocked(compositeCacheKey)) {
            Long blockedUntil = hotKeyCache.getBlockedUntil(compositeCacheKey);
            long resetAt = (blockedUntil != null) ? blockedUntil : (now + 5000L);
            long retryAfterSec = Math.max(1L, (long) Math.ceil((resetAt - now) / 1000.0));

            metrics.recordBlocked(rule.getId(), tier, rule.getAlgorithm().getIdentifier(), "hot_key_cache");
            sendRateLimitResponse(response, rule.getId(), tierLimit.getCapacity(), 0L, resetAt, retryAfterSec);
            return;
        }

        // 4. Rate Limiter Evaluation (Resilient Redis + Fallback)
        RateLimitResult result = limiterService.evaluate(rule, tier, identity, 1L);

        // 5. Attach RFC headers
        response.setHeader(HEADER_LIMIT, String.valueOf(tierLimit.getCapacity()));
        response.setHeader(HEADER_REMAINING, String.valueOf(result.remaining()));
        response.setHeader(HEADER_RESET, String.valueOf(result.resetAtMs() / 1000L));

        if (result.allowed()) {
            metrics.recordAllowed(rule.getId(), tier, rule.getAlgorithm().getIdentifier());
            filterChain.doFilter(request, response);
        } else {
            // Shield key in local cache to cut further Redis calls
            hotKeyCache.recordBlocked(compositeCacheKey, result.resetAtMs());

            long retryAfterSec = result.getRetryAfterSeconds(now);
            metrics.recordBlocked(rule.getId(), tier, rule.getAlgorithm().getIdentifier(), "rate_limited");
            sendRateLimitResponse(response, rule.getId(), tierLimit.getCapacity(), result.remaining(), result.resetAtMs(), retryAfterSec);
        }
    }

    private void sendRateLimitResponse(
            HttpServletResponse response,
            String ruleId,
            long limit,
            long remaining,
            long resetAtMs,
            long retryAfterSec
    ) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HEADER_LIMIT, String.valueOf(limit));
        response.setHeader(HEADER_REMAINING, String.valueOf(remaining));
        response.setHeader(HEADER_RESET, String.valueOf(resetAtMs / 1000L));
        response.setHeader(HEADER_RETRY_AFTER, String.valueOf(retryAfterSec));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", "Too Many Requests");
        body.put("message", String.format("Rate limit exceeded for rule '%s'. Try again in %d seconds.", ruleId, retryAfterSec));
        body.put("retryAfter", retryAfterSec);
        body.put("resetAtMs", resetAtMs);

        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.getWriter().flush();
    }

    private String resolveTier(HttpServletRequest request) {
        String tierHeader = request.getHeader(HEADER_USER_TIER);
        if (tierHeader != null && !tierHeader.isBlank()) {
            return tierHeader.trim().toLowerCase();
        }
        return "free";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
