local allowedHashKey = KEYS[1]
local waitingQueueKey = KEYS[2]
local expiryTimestamp = tonumber(ARGV[1])
local currentTimestamp = ARGV[2]

-- 만료된 토큰을 찾아서 입장 허용 해시에서 삭제
-- { token1, timestamp1, token2, timestamp2, ... }
local allTokens = redis.call('HGETALL', allowedHashKey)
local expiredCount = 0

for i = 1, #allTokens, 2 do
    local token = allTokens[i]
    local timestamp = tonumber(allTokens[i + 1])

    if timestamp and timestamp < expiryTimestamp then
        redis.call('HDEL', allowedHashKey, token)
        expiredCount = expiredCount + 1
    end
end

-- 제거한 토큰 개수만큼 다음 사용자들 입장 허용
for i = 1, expiredCount do
    -- 대기하는 사람 1명씩 가져오기
    local result = redis.call('ZPOPMIN', waitingQueueKey, 1)
    if #result > 0 then
        local nextToken = result[1]
        redis.call('HSET', allowedHashKey, nextToken, currentTimestamp)
    else
        -- 더 이상 대기하는 사람이 없다면 break
        break
    end
end

return expiredCount