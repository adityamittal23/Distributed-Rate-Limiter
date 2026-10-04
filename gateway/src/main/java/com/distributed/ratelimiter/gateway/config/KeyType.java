package com.distributed.ratelimiter.gateway.config;

/**
 * Identity extraction strategy type.
 */
public enum KeyType {
    API_KEY("api-key"),
    IP("ip"),
    JWT("jwt");

    private final String identifier;

    KeyType(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public static KeyType fromString(String text) {
        for (KeyType kt : values()) {
            if (kt.identifier.equalsIgnoreCase(text) || kt.name().equalsIgnoreCase(text)) {
                return kt;
            }
        }
        return API_KEY;
    }
}
