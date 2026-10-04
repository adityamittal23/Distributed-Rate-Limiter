package com.distributed.ratelimiter.gateway.config;

/**
 * Quota configuration for a specific tier (e.g. "free", "pro", "enterprise").
 */
public class TierLimit {

    private long capacity = 100;
    private double refillPerSecond = 10.0;
    private long limit = 100;
    private long windowSeconds = 60;

    public TierLimit() {}

    public TierLimit(long capacityOrLimit, double refillOrWindowSeconds) {
        this.capacity = capacityOrLimit;
        this.limit = capacityOrLimit;
        this.refillPerSecond = refillOrWindowSeconds;
        this.windowSeconds = (long) refillOrWindowSeconds;
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
}
