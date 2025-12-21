package com.tickatch.gateway_server.waiting_queue.infrastructure.redis;

import static com.tickatch.gateway_server.waiting_queue.application.exception.QueueErrorCode.USER_ID_NOT_FOUND;

import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
import com.tickatch.gateway_server.waiting_queue.application.dto.RemoveAllowedUserResult;
import com.tickatch.gateway_server.waiting_queue.application.dto.RemoveExpiredUsersResult;
import com.tickatch.gateway_server.waiting_queue.application.exception.QueueException;
import com.tickatch.gateway_server.waiting_queue.application.port.QueueRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@Slf4j
public class RedisQueueRepositoryImpl implements QueueRepository {

  private static final String COUNTER_KEY = "queue:counter";
  private static final String WAITING_QUEUE_KEY = "queue:wait";
  private static final String ALLOWED_IN_HASH_KEY = "allowedIn:users";
  private final int allowedInDurationSeconds;
  private final int allowedInUsersMaxCap;

  private final ReactiveRedisTemplate<String, String> redis;

  private final RedisScript<String> lineupScript;
  private final RedisScript<List> removeAllowedUserIdScript;
  private final RedisScript<List> cleanupExpiredUserIdsScript;

  public RedisQueueRepositoryImpl(
      ReactiveRedisTemplate<String, String> redis,
      @Value("${queue.max-capacity}") int maxCap,
      @Value("${queue.allowed-in-duration-seconds}") int durSec,
      RedisScript<String> lineupScript,
      RedisScript<List> removeAllowedUserIdScript,
      RedisScript<List> cleanupExpiredUserIdsScript

  ) {
    this.redis = redis;
    this.allowedInUsersMaxCap = maxCap;
    this.allowedInDurationSeconds = durSec;
    this.lineupScript = lineupScript;
    this.removeAllowedUserIdScript = removeAllowedUserIdScript;
    this.cleanupExpiredUserIdsScript = cleanupExpiredUserIdsScript;
  }

  //  같은 토큰으로 요청할 때마다 새로운 대기번호가 부여됨
  //  입장 가능하면 기다리지 않고 바로 입장
  public Mono<String> lineUp(String userId) {
    List<String> keys = Arrays.asList(ALLOWED_IN_HASH_KEY, COUNTER_KEY, WAITING_QUEUE_KEY);
    List<String> args = Arrays.asList(userId, String.valueOf(allowedInUsersMaxCap),
        String.valueOf(Instant.now().getEpochSecond()));

    return redis.execute(lineupScript, keys, args)
        .next()
        .map(result -> switch (result) {
          case "ALREADY_ALLOWED" -> "이미 입장 가능한 상태입니다.";
          case "ALLOWED" -> "바로 입장 가능합니다.";
          default -> "대기열에 등록되었습니다.";
        });
  }

  public Mono<QueueStatusResponse> getCurrentStatus(String userId) {
    // 순번 구하기
    Mono<Long> positionMono = redis.opsForZSet().rank(WAITING_QUEUE_KEY, userId)
        .switchIfEmpty(Mono.error(new QueueException(USER_ID_NOT_FOUND)))
        .map(pos -> pos + 1); // redis zset은 순번이 0부터 시작함

    // 큐 길이 구하기
    Mono<Long> queueSizeMono = redis.opsForZSet().size(WAITING_QUEUE_KEY);

    return Mono.zip(positionMono, queueSizeMono).map(tuple -> {
      Long userPos = tuple.getT1();
      Long queueSize = tuple.getT2();
      Long usersBehind = queueSize - userPos;

      return new QueueStatusResponse(queueSize, userPos, usersBehind);
    });
  }

  public Mono<Boolean> isAlreadyAllowedIn(String userId) {
    return redis.opsForHash().hasKey(ALLOWED_IN_HASH_KEY, userId);
  }

  public Mono<Void> refreshAllowedInTimestamp(String userId) {
    return redis.opsForHash().hasKey(ALLOWED_IN_HASH_KEY, userId)
        .flatMap(exists -> {
          if (exists) {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            return redis.opsForHash()
                .put(ALLOWED_IN_HASH_KEY, userId, timestamp)
                .then();
          }
          return Mono.empty();
        });
  }

  // 스케줄러가 주기적으로 호출할 메소드
  public Mono<RemoveExpiredUsersResult> cleanupExpiredUserIds() {
    long expiryTimestamp = Instant.now().getEpochSecond() - allowedInDurationSeconds;

    List<String> keys = Arrays.asList(ALLOWED_IN_HASH_KEY, WAITING_QUEUE_KEY);
    List<String> args = Arrays.asList(
        String.valueOf(expiryTimestamp),
        String.valueOf(Instant.now().getEpochSecond())
    );

    return redis.execute(cleanupExpiredUserIdsScript, keys, args)
        .next().flatMap(result -> Mono.just(new RemoveExpiredUsersResult((List<String>) result)));
  }

  public Mono<Boolean> removeWaitingUserId(String userId) {
    return redis.opsForZSet()
        .remove(WAITING_QUEUE_KEY, userId)
        .map(removed -> removed > 0)
        .onErrorReturn(false);
  }

  public Mono<RemoveAllowedUserResult> removeAllowedUserId(String userId) {
    List<String> keys = Arrays.asList(ALLOWED_IN_HASH_KEY, WAITING_QUEUE_KEY);
    List<String> args = Arrays.asList(userId, String.valueOf(Instant.now().getEpochSecond()));

    return redis.execute(removeAllowedUserIdScript, keys, args)
        .next()
        .flatMap(result -> {
          List<Object> resultList = (List<Object>) result;
          Long removed = Long.parseLong(resultList.get(0).toString());
          String nextUserId = resultList.get(1) != null ? resultList.get(1).toString() : null;

          if (removed > 0) {
            return Mono.just(new RemoveAllowedUserResult(true, nextUserId));
          }

          return Mono.just(new RemoveAllowedUserResult(false, null));
        })
        .onErrorReturn(new RemoveAllowedUserResult(false, null));
  }
}