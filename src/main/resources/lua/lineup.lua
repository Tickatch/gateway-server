local allowedHashKey = KEYS[1]
local counterKey = KEYS[2]
local waitingQueueKey = KEYS[3]
local userId = ARGV[1]
local maxCap = tonumber(ARGV[2])
local timestamp = ARGV[3]

-- 이미 입장 허용된 사용자인지 확인
local isAllowed = redis.call('HEXISTS', allowedHashKey, userId)
if isAllowed == 1 then
    return "ALREADY_ALLOWED"
end

-- 현재 입장 허용된 사용자 수 확인
local currentSize = redis.call('HLEN', allowedHashKey)

if currentSize < maxCap then
    -- 바로 입장 허용
    redis.call('HSET', allowedHashKey, userId, timestamp)
    return "ALLOWED"
else
    -- 대기열에 추가
    local seq = redis.call('INCR', counterKey)
    redis.call('ZADD', waitingQueueKey, seq, userId)
    return "QUEUED"
end