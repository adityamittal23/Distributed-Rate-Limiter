package com.distributed.ratelimiter.gateway.config;

import com.distributed.ratelimiter.core.AlgorithmType;

import java.util.HashMap;
import java.util.Map;

/**
 * Route-level rate limiting rule configuration definition.
 */
public class RuleDefinition {

    private String id;
    private String match = "/**";
    private String method = "*";
    private KeyType key = KeyType.API_KEY;
    private AlgorithmType algorithm = AlgorithmType.TOKEN_BUCKET;
    private FailurePolicy failurePolicy = FailurePolicy.FAIL_OPEN;

    // Default rate values
    private long capacity = 100;
    private double refillPerSecond = 10.0;
    private long limit = 100;
    private long windowSeconds = 60;

    // Optional tiered configurations (e.g. "free" -> 10/min, "pro" -> 1000/min)
    private Map<String, TierLimit> tiers = new HashMap<>();

    public RuleDefinition() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMatch() {
        return match;
    }

    public void setMatch(String match) {
        this.match = match;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public KeyType getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = KeyType.fromString(key);
    }

    public AlgorithmType getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = AlgorithmType.fromString(algorithm);
    }

    public FailurePolicy getFailurePolicy() {
        return failurePolicy;
    }

    public void setFailurePolicy(FailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
        this.limit = capacity;
    }

    public double getRefillPerSecond() {
        return refillPerSecond;
    }

    public void setRefillPerSecond(double refillPerSecond) {
        this.refillPerSecond = refillPerSecond;
    }

    public long getLimit() {
        return limit;
    }

    public void setLimit(long limit) {
        this.limit = limit;
        this.capacity = limit;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public Map<String, TierLimit> getTiers() {
        return tiers;
    }

    public void setTiers(Map<String, TierLimit> tiers) {
        this.tiers = tiers;
    }

    public TierLimit resolveTier(String tierName) {
        if (tierName != null && tiers.containsKey(tierName.toLowerCase())) {
            return tiers.get(tierName.toLowerCase());
        }
        // Fall back to rule defaults
        TierLimit defaultTier = new TierLimit();
        defaultTier.setCapacity(this.capacity);
        defaultTier.setLimit(this.limit);
        defaultTier.setRefillPerSecond(this.refillPerSecond);
        defaultTier.setWindowSeconds(this.windowSeconds);
        return defaultTier;
    }
}
