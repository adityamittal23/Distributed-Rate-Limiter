package com.distributed.ratelimiter.redis;

import com.distributed.ratelimiter.core.AlgorithmType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisKeyFormatterTest {

    @Test
    @DisplayName("Should format Redis keys with hash tags around identity for cluster compatibility")
    void testFormatKeyWithHashTag() {
        String key = RedisKeyFormatter.formatKey("api-default", "user-12345", AlgorithmType.TOKEN_BUCKET);
        assertThat(key).isEqualTo("rl:{user-12345}:token-bucket:api-default");
    }

    @Test
    @DisplayName("Should handle null or empty values gracefully with safe defaults")
    void testFormatKeyWithDefaults() {
        String key = RedisKeyFormatter.formatKey("", null, AlgorithmType.SLIDING_WINDOW_COUNTER);
        assertThat(key).isEqualTo("rl:{anonymous}:sliding-window-counter:default");
    }
}
