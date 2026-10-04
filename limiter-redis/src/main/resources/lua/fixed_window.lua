-- Fixed Window Rate Limiter Lua Script
-- KEYS[1]: bucket key (e.g. rl:{<id>}:fw:<rule>)
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

local current_window_start = math.floor(now / window_ms) * window_ms
local reset_at_ms = current_window_start + window_ms
local bucket_key = key .. ':' .. current_window_start

-- 2. Increment counter
local current_count = redis.call('INCRBY', bucket_key, cost)
if current_count == cost then
    -- Set TTL with 1 extra second buffer
    redis.call('PEXPIRE', bucket_key, window_ms + 1000)
end

local allowed = 0
local remaining = 0

if current_count <= limit then
    allowed = 1
    remaining = math.max(0, limit - current_count)
else
    allowed = 0
    remaining = 0
end

-- Return: [allowed (1 or 0), remaining, resetAtMs]
return { allowed, remaining, reset_at_ms }
