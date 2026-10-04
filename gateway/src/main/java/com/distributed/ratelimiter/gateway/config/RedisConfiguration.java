package com.distributed.ratelimiter.gateway.config;

import com.distributed.ratelimiter.redis.RedisLuaScriptExecutor;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Lettuce Redis Client and Lua script executor configuration.
 */
@Configuration
public class RedisConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisConfiguration.class);

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.timeout:2000ms}")
    private Duration timeout;

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient() {
        RedisURI redisUri = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withTimeout(timeout)
                .build();

        RedisClient client = RedisClient.create(redisUri);
        client.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(Duration.ofMillis(1000))
                        .keepAlive(true)
                        .build())
                .build());
        return client;
    }

    @Bean
    public RedisLuaScriptExecutor redisLuaScriptExecutor(RedisClient redisClient) {
        java.util.concurrent.atomic.AtomicReference<StatefulRedisConnection<String, String>> connectionRef = new java.util.concurrent.atomic.AtomicReference<>();
        return new RedisLuaScriptExecutor(() -> {
            StatefulRedisConnection<String, String> conn = connectionRef.get();
            if (conn != null && conn.isOpen()) {
                return conn.sync();
            }
            try {
                conn = redisClient.connect();
                connectionRef.set(conn);
                log.info("Successfully connected to Redis at {}:{}", redisHost, redisPort);
                return conn.sync();
            } catch (Exception e) {
                log.debug("Redis connection at {}:{} unavailable: {}", redisHost, redisPort, e.getMessage());
                return null;
            }
        });
    }
}
