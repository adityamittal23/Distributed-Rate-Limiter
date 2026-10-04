package com.distributed.ratelimiter.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Root configuration properties bound from ratelimiter in application.yml.
 */
@ConfigurationProperties(prefix = "ratelimiter")
public class RateLimiterProperties {

    private boolean enabled = true;
    private FailurePolicy defaultFailurePolicy = FailurePolicy.FAIL_OPEN;
    private List<RuleDefinition> rules = new ArrayList<>();
    private HotKeyProperties hotKey = new HotKeyProperties();

    public static class HotKeyProperties {
        private boolean enabled = true;
        private int cacheDurationSeconds = 5;
        private long maximumSize = 50_000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCacheDurationSeconds() {
            return cacheDurationSeconds;
        }

        public void setCacheDurationSeconds(int cacheDurationSeconds) {
            this.cacheDurationSeconds = cacheDurationSeconds;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FailurePolicy getDefaultFailurePolicy() {
        return defaultFailurePolicy;
    }

    public void setDefaultFailurePolicy(FailurePolicy defaultFailurePolicy) {
        this.defaultFailurePolicy = defaultFailurePolicy;
    }

    public List<RuleDefinition> getRules() {
        return rules;
    }

    public void setRules(List<RuleDefinition> rules) {
        this.rules = rules;
    }

    public HotKeyProperties getHotKey() {
        return hotKey;
    }

    public void setHotKey(HotKeyProperties hotKey) {
        this.hotKey = hotKey;
    }
}
