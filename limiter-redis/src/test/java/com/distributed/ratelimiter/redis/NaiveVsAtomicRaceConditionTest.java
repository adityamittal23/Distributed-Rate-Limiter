package com.distributed.ratelimiter.redis;

import com.distributed.ratelimiter.core.RateLimitResult;
import io.lettuce.core.api.sync.RedisStringCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class NaiveVsAtomicRaceConditionTest {

    @Mock
    private RedisStringCommands<String, String> stringCommands;

    @Test
    @DisplayName("Naive GET-then-SET rate limiter permits limit violations under concurrent load")
    void testNaiveRateLimiterRacesUnderConcurrency() throws InterruptedException {
        int limit = 5;
        int totalRequests = 25;

        Map<String, String> simulatedRedis = new ConcurrentHashMap<>();

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            try {
                // Simulate network latency between GET and evaluation
                Thread.sleep(2);
            } catch (InterruptedException ignored) {}
            return simulatedRedis.get(key);
        }).when(stringCommands).get(anyString());

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            try {
                // Simulate network latency for SET
                Thread.sleep(2);
            } catch (InterruptedException ignored) {}
            simulatedRedis.put(key, value);
            return "OK";
        }).when(stringCommands).set(anyString(), anyString());

        NaiveNonAtomicRateLimiter naiveLimiter = new NaiveNonAtomicRateLimiter(stringCommands, "test-rule", limit);

        ExecutorService executor = Executors.newFixedThreadPool(15);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RateLimitResult result = naiveLimiter.tryAcquire("abusive-client");
                    if (result.allowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // Due to the GET-then-SET race condition across threads, allowed requests will exceed limit!
        assertThat(allowedCount.get())
                .as("Naive rate limiter allows limit breaches under concurrent traffic")
                .isGreaterThan(limit);
    }
}
