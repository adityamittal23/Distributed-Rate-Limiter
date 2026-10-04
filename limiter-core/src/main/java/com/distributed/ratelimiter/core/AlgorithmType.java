package com.distributed.ratelimiter.core;

/**
 * Enumeration of supported rate limiting algorithm types.
 */
public enum AlgorithmType {
    TOKEN_BUCKET("token-bucket"),
    SLIDING_WINDOW_COUNTER("sliding-window-counter"),
    SLIDING_WINDOW_LOG("sliding-window-log"),
    FIXED_WINDOW("fixed-window");

    private final String identifier;

    AlgorithmType(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public static AlgorithmType fromString(String text) {
        for (AlgorithmType b : AlgorithmType.values()) {
            if (b.identifier.equalsIgnoreCase(text) || b.name().equalsIgnoreCase(text)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unknown algorithm type: " + text);
    }
}
