package com.tickatch.gateway_server.waiting_queue.infrastructure.redis;

import static com.tickatch.gateway_server.waiting_queue.application.exception.QueueErrorCode.TOKEN_NOT_FOUND;

import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
import com.tickatch.gateway_server.waiting_queue.application.exception.QueueException;
import com.tickatch.gateway_server.waiting_queue.application.port.QueueRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
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
  private final RedisScript<Long> removeAllowedTokenScript;

  public RedisQueueRepositoryImpl(
      ReactiveRedisTemplate<String, String> redis,
      @Value("${queue.max-capacity}") int maxCap,
      @Value("${queue.allowed-in-duration-seconds}") int durSec,
      RedisScript<String> lineupScript,
      RedisScript<Long> removeAllowedTokenScript
  ) {
    this.redis = redis;
    this.allowedInUsersMaxCap = maxCap;
    this.allowedInDurationSeconds = durSec;
    this.lineupScript = lineupScript;
    this.removeAllowedTokenScript = removeAllowedTokenScript;
  }

  //  같은 토큰으로 요청할 때마다 새로운 대기번호가 부여됨
  //  입장 가능하면 기다리지 않고 바로 입장
  public Mono<String> lineUp(String token) {
    List<String> keys = Arrays.asList(ALLOWED_IN_HASH_KEY, COUNTER_KEY, WAITING_QUEUE_KEY);
    List<String> args = Arrays.asList(token, String.valueOf(allowedInUsersMaxCap),
        String.valueOf(Instant.now().getEpochSecond()));

    return redis.execute(lineupScript, keys, args)
        .next()
        .map(result -> switch (result) {
          case "ALREADY_ALLOWED" -> "이미 입장 가능한 상태입니다.";
          case "ALLOWED" -> "바로 입장 가능합니다.";
          default -> "대기열에 등록되었습니다.";
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

  public Mono<Void> refreshAllowedInTimestamp(String token) {
    return redis.opsForHash().hasKey(ALLOWED_IN_HASH_KEY, token)
        .flatMap(exists -> {
          if (exists) {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            return redis.opsForHash()
                .put(ALLOWED_IN_HASH_KEY, token, timestamp)
                .then();
          }
          return Mono.empty();
        });
  }

  // 스케줄러가 주기적으로 호출할 메소드
  public Mono<Long> cleanupExpiredTokens() {
    long expiryTimestamp = Instant.now().getEpochSecond() - allowedInDurationSeconds;

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
                  .map(removed -> removed > 0 ? 1L : 0L);
            }
          } catch (NumberFormatException e) {
            // 잘못된 형식의 타임스탬프도 삭제
            return redis.opsForHash()
                .remove(ALLOWED_IN_HASH_KEY, token)
                .map(removed -> removed > 0 ? 1L : 0L);
          }
          return Mono.just(0L);
        })
        .reduce(0L, Long::sum);
  }

  public Mono<Boolean> removeWaitingToken(String token) {
    return redis.opsForZSet().remove(WAITING_QUEUE_KEY, token).flatMap(removed -> {
          if (removed > 0) {
            log.info("대기열 토큰 제거 완료: {}", token);
            return allowNextUser().thenReturn(true);
          }
          return Mono.just(false);
        })
        .doOnError(error -> log.error("대기열 토큰 제거 중 오류 발생: {}", token, error));
  }

  public Mono<Boolean> removeAllowedToken(String token) {
    List<String> keys = Arrays.asList(ALLOWED_IN_HASH_KEY, WAITING_QUEUE_KEY);
    List<String> args = Arrays.asList(token, String.valueOf(Instant.now().getEpochSecond()));

    return redis.execute(removeAllowedTokenScript, keys, args)
        .next()
        .map(removed -> removed > 0)
        .onErrorReturn(false);
  }
}