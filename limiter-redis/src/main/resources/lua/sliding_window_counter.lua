-- Sliding Window Counter Rate Limiter Lua Script
-- KEYS[1]: bucket key (e.g. rl:{<id>}:sc:<rule>)
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

local current_window_index = math.floor(now / window_ms)
local reset_at_ms = (current_window_index + 1) * window_ms

-- 2. Fetch stored window state
local data = redis.call('HMGET', key, 'windowIndex', 'curCount', 'prevCount')
local stored_index = tonumber(data[1])
local stored_cur = tonumber(data[2])
local stored_prev = tonumber(data[3])

local cur_count = 0
local prev_count = 0

if stored_index == nil then
    cur_count = 0
    prev_count = 0
elseif stored_index == current_window_index then
    cur_count = stored_cur or 0
    prev_count = stored_prev or 0
elseif stored_index == current_window_index - 1 then
    cur_count = 0
    prev_count = stored_cur or 0
else
    cur_count = 0
    prev_count = 0
end

-- 3. Calculate weighted count
local elapsed_in_window = now % window_ms
local weight = math.max(0.0, 1.0 - (elapsed_in_window / window_ms))
local estimated_count = (prev_count * weight) + cur_count

local allowed = 0
local remaining = 0

if (estimated_count + cost) <= limit then
    allowed = 1
    cur_count = cur_count + cost
    remaining = math.max(0, limit - math.floor(estimated_count + cost))
else
    allowed = 0
    remaining = math.max(0, limit - math.floor(estimated_count))
end

-- 4. Store state with TTL (2x window duration)
redis.call('HMSET', key, 'windowIndex', current_window_index, 'curCount', cur_count, 'prevCount', prev_count)
redis.call('PEXPIRE', key, window_ms * 2)

-- Return: [allowed (1 or 0), remaining, resetAtMs]
return { allowed, remaining, reset_at_ms }
