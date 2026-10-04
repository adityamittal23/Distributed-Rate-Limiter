package com.distributed.ratelimiter.redis;

import com.distributed.ratelimiter.core.RateLimitResult;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisScriptingCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisLuaScriptExecutorTest {

    @Mock
    private RedisScriptingCommands<String, String> scriptingCommands;

    @Test
    @DisplayName("Should execute script via EVALSHA and cache digest")
    void testExecuteScriptWithShaCache() {
        RedisLuaScriptExecutor executor = new RedisLuaScriptExecutor(scriptingCommands);

        when(scriptingCommands.scriptLoad(anyString())).thenReturn("sha12345");
        doAnswer(invocation -> List.of(1L, 9L, 1700000000000L))
                .when(scriptingCommands)
                .evalsha(eq("sha12345"), eq(ScriptOutputType.MULTI), any(String[].class), any(String.class), any(String.class), any(String.class));

        String[] keys = new String[]{"rl:{user1}:tb:default"};
        String[] args = new String[]{"10", "1.0", "1"};

        RateLimitResult result1 = executor.executeRateLimitScript("lua/token_bucket.lua", keys, args);
        assertThat(result1.allowed()).isTrue();
        assertThat(result1.remaining()).isEqualTo(9);

        // Second call should reuse cached SHA without calling scriptLoad again
        RateLimitResult result2 = executor.executeRateLimitScript("lua/token_bucket.lua", keys, args);
        assertThat(result2.allowed()).isTrue();

        verify(scriptingCommands, times(1)).scriptLoad(anyString());
    }

    @Test
    @DisplayName("Should reload script and retry when Redis returns NOSCRIPT")
    void testNoscriptHandlingAndRecovery() {
        RedisLuaScriptExecutor executor = new RedisLuaScriptExecutor(scriptingCommands);

        when(scriptingCommands.scriptLoad(anyString()))
                .thenReturn("shaInitial")
                .thenReturn("shaReloaded");

        // First evalsha call fails with NOSCRIPT
        doThrow(new RedisCommandExecutionException("NOSCRIPT No matching script. Please use EVAL."))
                .when(scriptingCommands)
                .evalsha(eq("shaInitial"), eq(ScriptOutputType.MULTI), any(String[].class), any(String.class), any(String.class), any(String.class));

        // Second evalsha call with reloaded sha succeeds
        doAnswer(invocation -> List.of(1L, 5L, 1700000000000L))
                .when(scriptingCommands)
                .evalsha(eq("shaReloaded"), eq(ScriptOutputType.MULTI), any(String[].class), any(String.class), any(String.class), any(String.class));

        String[] keys = new String[]{"rl:{user2}:tb:default"};
        String[] args = new String[]{"10", "1.0", "1"};

        RateLimitResult result = executor.executeRateLimitScript("lua/token_bucket.lua", keys, args);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(5);

        // Verify scriptLoad was called twice (initial + reload)
        verify(scriptingCommands, times(2)).scriptLoad(anyString());
    }
}
