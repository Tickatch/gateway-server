local allowedHashKey = KEYS[1]
local waitingQueueKey = KEYS[2]
local expiryTimestamp = tonumber(ARGV[1])
local currentTimestamp = ARGV[2]

-- 만료된 userId들을 찾아서 입장 허용 해시에서 삭제
-- { userId1, timestamp1, userId2, timestamp2, ... }
local allUserIds = redis.call('HGETALL', allowedHashKey)
local expiredCount = 0

for i = 1, #allUserIds, 2 do
    local userId = allUserIds[i]
    local timestamp = tonumber(allUserIds[i + 1])

    if timestamp and timestamp < expiryTimestamp then
        redis.call('HDEL', allowedHashKey, userId)
        expiredCount = expiredCount + 1
    end
end

-- 제거한 userId 개수만큼 다음 사용자들 입장 허용
local allowedUserIds = {}

for i = 1, expiredCount do
    -- 대기하는 사람 1명씩 가져오기
    local result = redis.call('ZPOPMIN', waitingQueueKey, 1)
    if #result > 0 then
        local nextUserId = result[1]
        redis.call('HSET', allowedHashKey, nextUserId, currentTimestamp)
        table.insert(allowedUserIds, nextUserId)
    else
        -- 더 이상 대기하는 사람이 없다면 break
        break
    end
end

return allowedUserIds