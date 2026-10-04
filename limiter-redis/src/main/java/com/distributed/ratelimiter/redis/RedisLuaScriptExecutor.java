package com.distributed.ratelimiter.redis;

import com.distributed.ratelimiter.core.RateLimitResult;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisScriptingCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages loading, caching SHA1 digests, and executing Lua scripts via Lettuce.
 * Automatically recovers from Redis restarts/flushes when NOSCRIPT is encountered.
 */
public class RedisLuaScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(RedisLuaScriptExecutor.class);

    private final java.util.function.Supplier<RedisScriptingCommands<String, String>> commandsSupplier;
    private final Map<String, String> scriptShaCache = new ConcurrentHashMap<>();
    private final Map<String, String> scriptContentCache = new ConcurrentHashMap<>();

    public RedisLuaScriptExecutor(RedisScriptingCommands<String, String> scriptingCommands) {
        this(() -> scriptingCommands);
    }

    public RedisLuaScriptExecutor(java.util.function.Supplier<RedisScriptingCommands<String, String>> commandsSupplier) {
        this.commandsSupplier = commandsSupplier;
    }

    private RedisScriptingCommands<String, String> getCommands() {
        RedisScriptingCommands<String, String> commands = commandsSupplier.get();
        if (commands == null) {
            throw new IllegalStateException("Redis scripting commands are unavailable (Redis connection failed or offline)");
        }
        return commands;
    }

    /**
     * Executes the given Lua script with automatic SHA1 caching and NOSCRIPT recovery.
     *
     * @param scriptPath Classpath resource path to the Lua script (e.g. "lua/token_bucket.lua")
     * @param keys       Redis keys array
     * @param args       Arguments array
     * @return Evaluated RateLimitResult
     */
    public RateLimitResult executeRateLimitScript(String scriptPath, String[] keys, String[] args) {
        String sha = getOrLoadSha(scriptPath);
        try {
            return executeEvalSha(sha, keys, args);
        } catch (RedisCommandExecutionException ex) {
            if (ex.getMessage() != null && ex.getMessage().toUpperCase().contains("NOSCRIPT")) {
                log.warn("NOSCRIPT error encountered for {}. Reloading script and retrying...", scriptPath);
                scriptShaCache.remove(scriptPath);
                String newSha = getOrLoadSha(scriptPath);
                return executeEvalSha(newSha, keys, args);
            }
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private RateLimitResult executeEvalSha(String sha, String[] keys, String[] args) {
        List<Object> rawResult = getCommands().evalsha(
                sha,
                ScriptOutputType.MULTI,
                keys,
                args
        );

        if (rawResult == null || rawResult.size() < 3) {
            throw new IllegalStateException("Invalid Lua response format: " + rawResult);
        }

        long allowedCode = ((Number) rawResult.get(0)).longValue();
        long remaining = ((Number) rawResult.get(1)).longValue();
        long resetAtMs = ((Number) rawResult.get(2)).longValue();

        boolean allowed = (allowedCode == 1L);
        return allowed ? RateLimitResult.allowed(remaining, resetAtMs) : RateLimitResult.rejected(remaining, resetAtMs);
    }

    private String getOrLoadSha(String scriptPath) {
        return scriptShaCache.computeIfAbsent(scriptPath, path -> {
            String script = scriptContentCache.computeIfAbsent(path, this::readResourceFile);
            String sha = getCommands().scriptLoad(script);
            log.info("Preloaded Lua script [{}] with SHA1 digest: {}", path, sha);
            return sha;
        });
    }

    private String readResourceFile(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Script resource not found on classpath: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read Lua script from resource: " + resourcePath, e);
        }
    }
}
