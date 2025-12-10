package com.tickatch.gateway_server.waiting_queue.infrastructure.redis;

import static com.tickatch.gateway_server.waiting_queue.application.exception.QueueErrorCode.TOKEN_NOT_FOUND;

import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
import com.tickatch.gateway_server.waiting_queue.application.exception.QueueException;
import com.tickatch.gateway_server.waiting_queue.application.port.QueueRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisQueueRepositoryImpl implements QueueRepository {

  private static final String COUNTER_KEY = "queue:counter";
  private static final String WAITING_QUEUE_KEY = "queue:wait";
  private static final String ALLOWED_IN_HASH_KEY = "allowedIn:users";
  private static final long ALLOWED_IN_DURATION_SECONDS = 240L;

  @Value("${queue.max-capacity}")
  private int allowedInUsersMaxCap;

  private final ReactiveRedisTemplate<String, String> redis;

  // 입장을 더 허용할 수 있는지?
  public Mono<Boolean> canLetMoreEnter() {
    return redis.opsForHash()
        .size(ALLOWED_IN_HASH_KEY)
        .map(size -> size < allowedInUsersMaxCap)
        .defaultIfEmpty(true);
  }

  //  같은 토큰으로 요청할 때마다 새로운 대기번호가 부여됨
  //  입장 가능하면 기다리지 않고 바로 입장
  public Mono<Void> lineUp(String token) {
    return canLetMoreEnter().flatMap(canEnter -> {
      if (canEnter) {
        log.info("바로 입장함");
        return allowUserInWithToken(token);
      } else {
        return redis.opsForValue()
            .increment(COUNTER_KEY)
            .flatMap(seq ->
                redis.opsForZSet()
                    .add(WAITING_QUEUE_KEY, token, seq)
                    .then());
      }
    });
  }

  public Mono<QueueStatusResponse> getCurrentStatus(String token) {
    // 순번 구하기
    Mono<Long> positionMono = redis.opsForZSet().rank(WAITING_QUEUE_KEY, token)
        .switchIfEmpty(Mono.error(new QueueException(TOKEN_NOT_FOUND)))
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

  public Mono<Void> allowNextUser() {
    return redis.opsForZSet()
        .popMin(WAITING_QUEUE_KEY)  // 대기열 맨 앞에 있는 사용자를 빼기
        .flatMap(tuple -> {
          if(tuple.getValue() == null) return Mono.empty();
          String token = tuple.getValue();

          // 뺀 사용자의 정보로 입장 허용 처리
          return allowUserInWithToken(token);
        });
  }

  private Mono<Void> allowUserInWithToken(String token) {
    log.info("입장 허용 해시에 저장");
    String timestamp = String.valueOf(Instant.now().getEpochSecond());
    return redis.opsForHash()
        .put(ALLOWED_IN_HASH_KEY, token, timestamp)
        .then();
  }

  public Mono<Boolean> isAlreadyAllowedIn(String token) {
    return redis.opsForHash().hasKey(ALLOWED_IN_HASH_KEY, token);
  }

  // 스케줄러가 4분마다 호출할 메소드
  public Mono<Void> cleanupExpiredTokens() {
    long expiryTimestamp = Instant.now().getEpochSecond() - ALLOWED_IN_DURATION_SECONDS;

    return redis.opsForHash()
        .scan(ALLOWED_IN_HASH_KEY, ScanOptions.scanOptions().count(100).build())
        .flatMap(entry -> {
          String token = (String) entry.getKey();
          String timestampStr = (String) entry.getValue();

          try {
            long timestamp = Long.parseLong(timestampStr);
            if (timestamp < expiryTimestamp) {
              // 만료된 토큰 삭제
              return redis.opsForHash()
                  .remove(ALLOWED_IN_HASH_KEY, token)
                  .then();
            }
          } catch (NumberFormatException e) {
            // 잘못된 형식의 타임스탬프도 삭제
            return redis.opsForHash()
                .remove(ALLOWED_IN_HASH_KEY, token)
                .then();
          }
          return Mono.just(0L);
        })
        .then();
  }
}