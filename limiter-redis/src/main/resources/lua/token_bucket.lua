-- Token Bucket Rate Limiter Lua Script
-- KEYS[1]: bucket key (e.g. rl:{<id>}:tb:<rule>)
-- ARGV[1]: capacity (number of tokens max)
-- ARGV[2]: refill_per_sec (number of tokens added per second)
-- ARGV[3]: cost (number of tokens to consume)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_per_sec = tonumber(ARGV[2])
local cost = tonumber(ARGV[3])

-- 1. Fetch current time atomically from Redis to avoid client clock skew
local time_val = redis.call('TIME')
local now = (tonumber(time_val[1]) * 1000) + math.floor(tonumber(time_val[2]) / 1000)

-- 2. Fetch current tokens and last refill timestamp
local data = redis.call('HMGET', key, 'tokens', 'ts')
local current_tokens = tonumber(data[1])
local last_refill = tonumber(data[2])

if current_tokens == nil then
    current_tokens = capacity
    last_refill = now
else
    local elapsed_ms = math.max(0, now - last_refill)
    local replenished = (elapsed_ms / 1000.0) * refill_per_sec
    current_tokens = math.min(capacity, current_tokens + replenished)
    last_refill = now
end

-- 3. Check if enough tokens exist
local allowed = 0
local remaining = 0
local reset_at_ms = now

if current_tokens >= cost then
    allowed = 1
    current_tokens = current_tokens - cost
    remaining = math.floor(current_tokens)
    local needed_for_full = capacity - current_tokens
    local ms_to_full = math.ceil((needed_for_full / refill_per_sec) * 1000.0)
    reset_at_ms = now + ms_to_full
else
    allowed = 0
    remaining = math.floor(current_tokens)
    local deficit = cost - current_tokens
    local wait_ms = math.ceil((deficit / refill_per_sec) * 1000.0)
    reset_at_ms = now + wait_ms
end

-- 4. Store state back to Redis
redis.call('HMSET', key, 'tokens', current_tokens, 'ts', last_refill)

-- Set TTL: 2x time to reach full capacity, minimum 60 seconds
local ttl_seconds = math.max(60, math.ceil((capacity / refill_per_sec) * 2))
redis.call('EXPIRE', key, ttl_seconds)

-- Return: [allowed (1 or 0), remaining tokens, resetAtMs]
return { allowed, remaining, reset_at_ms }
