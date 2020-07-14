-- key ,相当于redis key
local tokens_key = KEYS[1]   -- 资源ID
local timestamp_key = KEYS[2]  -- 资源时间戳
--redis.log(redis.LOG_WARNING, "tokens_key " .. tokens_key)

-- args  相当于redis param
local rate = tonumber(ARGV[1])   -- 令牌桶填充速率
local capacity = tonumber(ARGV[2])  -- 令牌桶最大容量
local now = tonumber(ARGV[3])   --当前时间
local requested = tonumber(ARGV[4])   -- 请求的令牌数

local fill_time = capacity/rate  -- 表示按照指定速率，需要多长时间，会填充满整个桶
local ttl = math.floor(fill_time*2)  -- 过期时间  2倍于填满桶的时间

--redis.log(redis.LOG_WARNING, "rate " .. ARGV[1])
--redis.log(redis.LOG_WARNING, "capacity " .. ARGV[2])
--redis.log(redis.LOG_WARNING, "now " .. ARGV[3])
--redis.log(redis.LOG_WARNING, "requested " .. ARGV[4])
--redis.log(redis.LOG_WARNING, "filltime " .. fill_time)
--redis.log(redis.LOG_WARNING, "ttl " .. ttl)

local last_tokens = tonumber(redis.call("get", tokens_key))  -- 获取剩余的令牌数
if last_tokens == nil then
  last_tokens = capacity  -- 如果不存在对应的Key， 默认等于令牌桶最大容量
end
--redis.log(redis.LOG_WARNING, "last_tokens " .. last_tokens)

local last_refreshed = tonumber(redis.call("get", timestamp_key))  -- 获取最后一次消费令牌时间
if last_refreshed == nil then
  last_refreshed = 0  -- 默认为0
end
--redis.log(redis.LOG_WARNING, "last_refreshed " .. last_refreshed)

local delta = math.max(0, now-last_refreshed) -- 表示最后一次消息令牌距离现在 过去了多久
local filled_tokens = math.min(capacity, last_tokens+(delta*rate))   --  delta*rate 表示在已经过去的时间，应该填充多少了token ， 加上last_tokens 表示现在总共能剩余的token
local allowed = filled_tokens >= requested --现在剩下的token和本次请求的token对比
local new_tokens = filled_tokens --新的剩余token
local allowed_num = 0
if allowed then
  new_tokens = filled_tokens - requested   -- 剩余的token 减去 本地请求的
  allowed_num = 1
end

--redis.log(redis.LOG_WARNING, "delta " .. delta)
--redis.log(redis.LOG_WARNING, "filled_tokens " .. filled_tokens)
--redis.log(redis.LOG_WARNING, "allowed_num " .. allowed_num)
--redis.log(redis.LOG_WARNING, "new_tokens " .. new_tokens)

redis.call("setex", tokens_key, ttl, new_tokens)  -- 设置新的剩余的令牌数
redis.call("setex", timestamp_key, ttl, now)  --设置最后一次消费令牌的时间

return { allowed_num, new_tokens }
