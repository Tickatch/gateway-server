local allowedHashKey = KEYS[1]
local waitingQueueKey = KEYS[2]
local userId = ARGV[1]
local timestamp = ARGV[2]

-- 입장 허용 해시에서 userId 제거
local removed = redis.call('HDEL', allowedHashKey, userId)

if removed == 0 then
    return {0, nil}
end

-- 다음 사용자 입장 허용 (ZPOPMIN은 [member, score] 형식으로 배열을 반환)
local result = redis.call('ZPOPMIN', waitingQueueKey, 1)

-- #result = {member, score}
if #result > 0 then
    local nextUserId = result[1]
    redis.call('HSET', allowedHashKey, nextUserId, timestamp)

    -- 삭제 성공 + 다음 대기자의 userId 반환
    return {1, nextUserId}
end

-- 삭제는 성공했지만, 다음 대기자가 없음
return {1, nil}