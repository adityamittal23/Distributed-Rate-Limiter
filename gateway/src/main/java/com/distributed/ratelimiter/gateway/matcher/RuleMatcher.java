package com.distributed.ratelimiter.gateway.matcher;

import com.distributed.ratelimiter.gateway.config.RateLimiterProperties;
import com.distributed.ratelimiter.gateway.config.RuleDefinition;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Optional;

/**
 * Matches incoming HTTP requests against configured Ant-style path patterns and HTTP methods.
 */
public class RuleMatcher {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<RuleDefinition> rules;

    public RuleMatcher(RateLimiterProperties properties) {
        this.rules = properties.getRules() != null ? properties.getRules() : List.of();
    }

    /**
     * Resolves the first matching rate limiting rule for the request path and method.
     */
    public Optional<RuleDefinition> findMatchingRule(String path, String httpMethod) {
        for (RuleDefinition rule : rules) {
            if (matches(rule, path, httpMethod)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    private boolean matches(RuleDefinition rule, String path, String httpMethod) {
        // 1. Method check
        if (!"*".equalsIgnoreCase(rule.getMethod()) &&
                !rule.getMethod().equalsIgnoreCase(httpMethod)) {
            return false;
        }

        // 2. Ant path pattern match
        return pathMatcher.match(rule.getMatch(), path);
    }
}
