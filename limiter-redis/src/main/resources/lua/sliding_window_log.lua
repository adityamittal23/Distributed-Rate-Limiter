-- Sliding Window Log Rate Limiter Lua Script
-- KEYS[1]: bucket key (e.g. rl:{<id>}:sl:<rule>)
-- ARGV[1]: limit (max requests allowed in window)
-- ARGV[2]: window_ms (window duration in milliseconds)
-- ARGV[3]: cost (number of units to consume)

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local cost = tonumber(ARGV[3])

-- 1. Fetch current time atomically from Redis
local time_val = redis.call('TIME')
local now = (tonumber(time_val[1]) * 1000) + math.floor(tonumber(time_val[2]) / 1000)

-- 2. Evict entries older than (now - window_ms)
local clear_before = now - window_ms
redis.call('ZREMRANGEBYSCORE', key, 0, clear_before)

-- 3. Check current count in sorted set
local current_count = redis.call('ZCARD', key)
local allowed = 0
local remaining = 0
local reset_at_ms = now + window_ms

if (current_count + cost) <= limit then
    allowed = 1
    -- Insert members for each cost unit
    for i = 1, cost do
        -- Unique member value using timestamp and index/random
        local member = string.format("%d-%d-%d", now, i, redis.call('INCR', key .. ':seq'))
        redis.call('ZADD', key, now, member)
    end
    remaining = math.max(0, limit - (current_count + cost))
    redis.call('PEXPIRE', key, window_ms)
    redis.call('PEXPIRE', key .. ':seq', window_ms)
else
    allowed = 0
    remaining = math.max(0, limit - current_count)
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    if oldest and oldest[2] then
        reset_at_ms = tonumber(oldest[2]) + window_ms
    end
end

-- Return: [allowed (1 or 0), remaining, resetAtMs]
return { allowed, remaining, reset_at_ms }
