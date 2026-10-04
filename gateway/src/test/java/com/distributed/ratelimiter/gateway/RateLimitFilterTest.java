package com.distributed.ratelimiter.gateway;

import com.distributed.ratelimiter.core.AlgorithmType;
import com.distributed.ratelimiter.core.RateLimitResult;
import com.distributed.ratelimiter.gateway.cache.HotKeyCache;
import com.distributed.ratelimiter.gateway.config.FailurePolicy;
import com.distributed.ratelimiter.gateway.config.KeyType;
import com.distributed.ratelimiter.gateway.config.RateLimiterProperties;
import com.distributed.ratelimiter.gateway.config.RuleDefinition;
import com.distributed.ratelimiter.gateway.config.TierLimit;
import com.distributed.ratelimiter.gateway.extractor.CompositeKeyExtractor;
import com.distributed.ratelimiter.gateway.filter.RateLimitFilter;
import com.distributed.ratelimiter.gateway.matcher.RuleMatcher;
import com.distributed.ratelimiter.gateway.metrics.RateLimiterMetrics;
import com.distributed.ratelimiter.gateway.resilience.RateLimiterEvaluationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimiterEvaluationService limiterService;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;
    private HotKeyCache hotKeyCache;

    @BeforeEach
    void setUp() {
        RuleDefinition rule = new RuleDefinition();
        rule.setId("api-default");
        rule.setMatch("/api/**");
        rule.setMethod("*");
        rule.setKey("api-key");
        rule.setAlgorithm("token-bucket");
        rule.setFailurePolicy(FailurePolicy.FAIL_OPEN);
        rule.setCapacity(10);
        rule.setRefillPerSecond(1.0);

        TierLimit proTier = new TierLimit();
        proTier.setCapacity(100);
        proTier.setRefillPerSecond(20.0);
        rule.setTiers(Map.of("pro", proTier));

        RateLimiterProperties props = new RateLimiterProperties();
        props.setRules(List.of(rule));

        RuleMatcher ruleMatcher = new RuleMatcher(props);
        CompositeKeyExtractor keyExtractor = new CompositeKeyExtractor();
        hotKeyCache = new HotKeyCache(true, 5, 1000L);
        RateLimiterMetrics metrics = new RateLimiterMetrics(new SimpleMeterRegistry());

        filter = new RateLimitFilter(ruleMatcher, keyExtractor, hotKeyCache, limiterService, metrics);
    }

    @Test
    @DisplayName("Allowed request should attach RFC headers and proceed down filter chain")
    void testAllowedRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        request.addHeader("X-API-Key", "key-client-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        long resetAt = System.currentTimeMillis() + 60_000L;
        when(limiterService.evaluate(any(RuleDefinition.class), eq("free"), eq("key-client-1"), eq(1L)))
                .thenReturn(RateLimitResult.allowed(9, resetAt));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(RateLimitFilter.HEADER_LIMIT)).isEqualTo("10");
        assertThat(response.getHeader(RateLimitFilter.HEADER_REMAINING)).isEqualTo("9");
        assertThat(response.getHeader(RateLimitFilter.HEADER_RESET)).isEqualTo(String.valueOf(resetAt / 1000L));

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Rejected request should return HTTP 429 with Retry-After and JSON body")
    void testRejectedRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        request.addHeader("X-API-Key", "key-client-2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        long resetAt = System.currentTimeMillis() + 10_000L;
        when(limiterService.evaluate(any(RuleDefinition.class), eq("free"), eq("key-client-2"), eq(1L)))
                .thenReturn(RateLimitResult.rejected(0, resetAt));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(RateLimitFilter.HEADER_RETRY_AFTER)).isNotNull();
        assertThat(response.getContentAsString()).contains("Too Many Requests");
        assertThat(response.getContentAsString()).contains("Rate limit exceeded for rule 'api-default'");

        // Filter chain should NOT be invoked when rate limited
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("HotKeyCache should fast-drop blocked requests without querying rate limiter service")
    void testHotKeyCacheFastDrop() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        request.addHeader("X-API-Key", "abusive-key");
        MockHttpServletResponse response1 = new MockHttpServletResponse();

        long resetAt = System.currentTimeMillis() + 10_000L;
        when(limiterService.evaluate(any(), any(), eq("abusive-key"), eq(1L)))
                .thenReturn(RateLimitResult.rejected(0, resetAt));

        // First request hits service and gets rejected -> placed into HotKeyCache
        filter.doFilter(request, response1, filterChain);
        assertThat(response1.getStatus()).isEqualTo(429);
        verify(limiterService, times(1)).evaluate(any(), any(), any(), eq(1L));

        // Second request for the same key within block duration should be fast-dropped by cache!
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilter(request, response2, filterChain);
        assertThat(response2.getStatus()).isEqualTo(429);

        // evaluate() was still only called ONCE!
        verify(limiterService, times(1)).evaluate(any(), any(), any(), eq(1L));
    }

    @Test
    @DisplayName("Tier header X-User-Tier: pro should apply Pro tier capacity")
    void testProTierRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pro-feature");
        request.addHeader("X-API-Key", "pro-user-1");
        request.addHeader("X-User-Tier", "pro");
        MockHttpServletResponse response = new MockHttpServletResponse();

        long resetAt = System.currentTimeMillis() + 60_000L;
        when(limiterService.evaluate(any(RuleDefinition.class), eq("pro"), eq("pro-user-1"), eq(1L)))
                .thenReturn(RateLimitResult.allowed(99, resetAt));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(RateLimitFilter.HEADER_LIMIT)).isEqualTo("100");
        assertThat(response.getHeader(RateLimitFilter.HEADER_REMAINING)).isEqualTo("99");
    }
}
