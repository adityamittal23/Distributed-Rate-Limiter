package com.distributed.ratelimiter.gateway.config;

import com.distributed.ratelimiter.gateway.cache.HotKeyCache;
import com.distributed.ratelimiter.gateway.extractor.CompositeKeyExtractor;
import com.distributed.ratelimiter.gateway.filter.RateLimitFilter;
import com.distributed.ratelimiter.gateway.matcher.RuleMatcher;
import com.distributed.ratelimiter.gateway.metrics.RateLimiterMetrics;
import com.distributed.ratelimiter.gateway.resilience.RateLimiterEvaluationService;
import com.distributed.ratelimiter.gateway.resilience.ResilientRateLimiterService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot configuration wiring gateway components.
 */
@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class GatewayConfiguration {

    @Bean
    public RuleMatcher ruleMatcher(RateLimiterProperties properties) {
        return new RuleMatcher(properties);
    }

    @Bean
    public CompositeKeyExtractor compositeKeyExtractor() {
        return new CompositeKeyExtractor();
    }

    @Bean
    public HotKeyCache hotKeyCache(RateLimiterProperties properties) {
        RateLimiterProperties.HotKeyProperties hotKey = properties.getHotKey();
        return new HotKeyCache(
                hotKey.isEnabled(),
                hotKey.getCacheDurationSeconds(),
                hotKey.getMaximumSize()
        );
    }

    @Bean
    public RateLimitFilter rateLimitFilter(
            RuleMatcher ruleMatcher,
            CompositeKeyExtractor compositeKeyExtractor,
            HotKeyCache hotKeyCache,
            RateLimiterEvaluationService limiterService,
            RateLimiterMetrics metrics
    ) {
        return new RateLimitFilter(ruleMatcher, compositeKeyExtractor, hotKeyCache, limiterService, metrics);
    }
}
