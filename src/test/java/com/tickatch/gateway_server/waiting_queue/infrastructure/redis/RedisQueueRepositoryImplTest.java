package com.tickatch.gateway_server.waiting_queue.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.tickatch.gateway_server.global.util.HmacUtil;
import com.tickatch.gateway_server.waiting_queue.application.exception.QueueException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

@SpringBootTest
@TestPropertySource(
    properties = {"queue.max-capacity=3", "queue.secret-key=test-secret-key-for-hmac"}
)
class RedisQueueRepositoryImplTest {

  @Autowired
  private RedisQueueRepositoryImpl queueRepository;

  @Autowired
  private ReactiveRedisTemplate<String, String> redis;

  private static final String COUNTER_KEY = "queue:counter";
  private static final String WAITING_QUEUE_KEY = "queue:wait";
  private static final String ALLOWED_IN_HASH_KEY = "allowedIn:users";

  @BeforeEach
  void setUp() {
    redis.delete(COUNTER_KEY).block();
    redis.delete(WAITING_QUEUE_KEY).block();
    redis.delete(ALLOWED_IN_HASH_KEY).block();
  }

  @Test
  @DisplayName("입장 가능하면 바로 입장, 불가능하면 대기열 추가")
  void lineUp_ShouldAllowOrQueue() {
    // given: 3명까지 바로 입장 가능
    queueRepository.lineUp("user-1").block();
    queueRepository.lineUp("user-2").block();
    queueRepository.lineUp("user-3").block();

    // when: 4번째 사용자는 대기열 추가
    queueRepository.lineUp("user-4").block();

    // then
    StepVerifier.create(queueRepository.isAlreadyAllowedIn("user-1"))
        .expectNext(true)
        .verifyComplete();

    StepVerifier.create(queueRepository.isInQueue("user-4"))
        .expectNext(true)
        .verifyComplete();
  }

  @Test
  @DisplayName("대기열 상태 조회 - 순번과 큐 크기")
  void getCurrentStatus_ShouldReturnCorrectPosition() {
    // given
    fillCapacity();
    queueRepository.lineUp("user-1").block();
    queueRepository.lineUp("user-2").block();

    // when & then
    StepVerifier.create(queueRepository.getCurrentStatus("user-1"))
        .assertNext(status -> {
          assertThat(status.userQueuePosition()).isEqualTo(1L);
          assertThat(status.totalQueueSize()).isEqualTo(2L);
          assertThat(status.usersBehind()).isEqualTo(1L);
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("다음 사용자 입장 허용 - 대기열에서 제거되고 입장 허용됨")
  void allowNextUser_ShouldMoveFromQueueToAllowed() {
    // given
    fillCapacity();
    queueRepository.lineUp("waiting-user").block();

    // when
    queueRepository.allowNextUser().block();

    // then
    StepVerifier.create(queueRepository.isInQueue("waiting-user"))
        .expectNext(false)
        .verifyComplete();

    StepVerifier.create(queueRepository.isAlreadyAllowedIn("waiting-user"))
        .expectNext(true)
        .verifyComplete();
  }

  @Test
  @DisplayName("만료된 토큰 정리")
  void cleanupExpiredTokens_ShouldRemoveExpired() {
    // given: 만료된 토큰과 유효한 토큰
    long expiredTime = Instant.now().getEpochSecond() - 300L; // 5분 전
    long validTime = Instant.now().getEpochSecond() - 60L;    // 1분 전

    redis.opsForHash().put(ALLOWED_IN_HASH_KEY, "expired", String.valueOf(expiredTime)).block();
    redis.opsForHash().put(ALLOWED_IN_HASH_KEY, "valid", String.valueOf(validTime)).block();

    // when
    queueRepository.cleanupExpiredTokens().block();

    // then
    StepVerifier.create(queueRepository.isAlreadyAllowedIn("expired"))
        .expectNext(false)
        .verifyComplete();

    StepVerifier.create(queueRepository.isAlreadyAllowedIn("valid"))
        .expectNext(true)
        .verifyComplete();
  }

  @Test
  @DisplayName("전체 플로우: 입장 → 대기 → 입장 허용")
  void fullQueueFlow() {
    // 1. 처음 3명 바로 입장
    queueRepository.lineUp("user-1").block();
    queueRepository.lineUp("user-2").block();
    queueRepository.lineUp("user-3").block();

    // 2. 4번째는 대기
    queueRepository.lineUp("user-4").block();

    StepVerifier.create(queueRepository.getCurrentStatus("user-4"))
        .assertNext(status -> assertThat(status.userQueuePosition()).isEqualTo(1L))
        .verifyComplete();

    // 3. 다음 사용자 입장 허용
    queueRepository.allowNextUser().block();

    // 4. user-4 입장됨
    StepVerifier.create(queueRepository.isAlreadyAllowedIn("user-4"))
        .expectNext(true)
        .verifyComplete();
  }

  private void fillCapacity() {
    queueRepository.lineUp("fill-1").block();
    queueRepository.lineUp("fill-2").block();
    queueRepository.lineUp("fill-3").block();
  }
}