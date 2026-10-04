package com.distributed.ratelimiter.gateway.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Local high-performance L1 cache to shield Redis from high-frequency abusive clients.
 * Caches "blocked-until" timestamps so repeated requests from rate-limited clients
 * are instantly dropped in-memory with sub-microsecond latency.
 */
public class HotKeyCache {

    private static final Logger log = LoggerFactory.getLogger(HotKeyCache.class);

    private final Cache<String, Long> blockedUntilCache;
    private final boolean enabled;

    public HotKeyCache(boolean enabled, int durationSeconds, long maximumSize) {
        this.enabled = enabled;
        this.blockedUntilCache = Caffeine.newBuilder()
                .expireAfterWrite(durationSeconds, TimeUnit.SECONDS)
                .maximumSize(maximumSize)
                .recordStats()
                .build();
    }

    public HotKeyCache() {
        this(true, 5, 50_000L);
    }

    /**
     * Checks if the given key is currently locally recorded as blocked.
     *
     * @param compositeKey Unique key (e.g. rule:identity).
     * @return true if the client is currently blocked and within the block window.
     */
    public boolean isBlocked(String compositeKey) {
        if (!enabled) {
            return false;
        }
        Long blockedUntil = blockedUntilCache.getIfPresent(compositeKey);
        if (blockedUntil == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < blockedUntil) {
            return true;
        } else {
            blockedUntilCache.invalidate(compositeKey);
            return false;
        }
    }

    /**
     * Gets the epoch millisecond until which the key is blocked.
     */
    public Long getBlockedUntil(String compositeKey) {
        if (!enabled) {
            return null;
        }
        Long blockedUntil = blockedUntilCache.getIfPresent(compositeKey);
        if (blockedUntil != null && System.currentTimeMillis() < blockedUntil) {
            return blockedUntil;
        }
        return null;
    }

    /**
     * Records a key as blocked until the specified epoch millisecond.
     */
    public void recordBlocked(String compositeKey, long blockedUntilEpochMs) {
        if (!enabled) {
            return;
        }
        blockedUntilCache.put(compositeKey, blockedUntilEpochMs);
        log.debug("HotKeyCache shielded key [{}] until {}", compositeKey, blockedUntilEpochMs);
    }

    public void invalidate(String compositeKey) {
        blockedUntilCache.invalidate(compositeKey);
    }

    public void clear() {
        blockedUntilCache.invalidateAll();
    }
}
