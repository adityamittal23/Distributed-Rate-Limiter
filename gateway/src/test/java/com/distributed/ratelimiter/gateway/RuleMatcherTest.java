package com.distributed.ratelimiter.gateway;

import com.distributed.ratelimiter.gateway.config.RateLimiterProperties;
import com.distributed.ratelimiter.gateway.config.RuleDefinition;
import com.distributed.ratelimiter.gateway.matcher.RuleMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RuleMatcherTest {

    @Test
    @DisplayName("Should match Ant path patterns and HTTP methods correctly")
    void testRuleMatching() {
        RuleDefinition rule1 = new RuleDefinition();
        rule1.setId("api-rule");
        rule1.setMatch("/api/**");
        rule1.setMethod("*");

        RuleDefinition rule2 = new RuleDefinition();
        rule2.setId("login-rule");
        rule2.setMatch("/auth/login");
        rule2.setMethod("POST");

        RateLimiterProperties props = new RateLimiterProperties();
        props.setRules(List.of(rule1, rule2));

        RuleMatcher matcher = new RuleMatcher(props);

        Optional<RuleDefinition> match1 = matcher.findMatchingRule("/api/v1/orders", "GET");
        assertThat(match1).isPresent();
        assertThat(match1.get().getId()).isEqualTo("api-rule");

        Optional<RuleDefinition> match2 = matcher.findMatchingRule("/auth/login", "POST");
        assertThat(match2).isPresent();
        assertThat(match2.get().getId()).isEqualTo("login-rule");

        // GET /auth/login should not match rule2 because method is POST
        Optional<RuleDefinition> match3 = matcher.findMatchingRule("/auth/login", "GET");
        assertThat(match3).isEmpty();

        // Unknown route
        Optional<RuleDefinition> match4 = matcher.findMatchingRule("/unmatched/path", "GET");
        assertThat(match4).isEmpty();
    }
}
