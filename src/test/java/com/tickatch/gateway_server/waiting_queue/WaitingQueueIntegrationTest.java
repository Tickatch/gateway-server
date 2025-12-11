package com.tickatch.gateway_server.waiting_queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.tickatch.gateway_server.global.util.HmacUtil;
import com.tickatch.gateway_server.waiting_queue.application.WaitingQueueService;
import com.tickatch.gateway_server.waiting_queue.application.exception.QueueException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

// 테스트 편의를 위해 1명만 입장 허용 가능하도록 설정
@SpringBootTest
@TestPropertySource(
    properties = {
        "queue.max-capacity=1",
        "queue.secret-key=test-secret-key-for-hmac",
        "queue.allowed-in-duration-seconds=15"
    }
)
class WaitingQueueIntegrationTest {

  @Autowired
  private WaitingQueueService queueService;

  @Autowired
  private ReactiveRedisTemplate<String, String> redis;

  private static final String USER_ID_1 = "user1";
  private static final String USER_ID_2 = "user2";
  private static final String USER_ID_3 = "user3";

  @BeforeEach
  void setUp() {
    redis.delete("queue:counter").block();
    redis.delete("queue:wait").block();
    redis.delete("allowedIn:users").block();
  }

  @Test
  @DisplayName("입장 허용 목록이 비어있다면 사용자가 대기열에 등록 후 바로 입장 가능하다.")
  void lineUp_UserEntersQueue() {
    // when: 사용자1이 대기열에 등록
    StepVerifier.create(queueService.lineUp(USER_ID_1))
        .assertNext(message -> assertThat(message).isEqualTo("대기열에 등록되었습니다."))
        .verifyComplete();

    // then: 입장 가능 여부 확인
    StepVerifier.create(queueService.canEnter(USER_ID_1))
        .assertNext(canEnter -> assertThat(canEnter).isTrue())
        .verifyComplete();
  }

  @Test
  @DisplayName("여러 사용자가 대기열에 등록하면 순서대로 대기 번호가 부여된다")
  void lineUp_MultipleUsers_GetSequentialPositions() {
    // given:
    // 최대 수용 인원을 1명으로 설정하여 대기열 강제 생성

    // when:
    // 3명의 사용자가 순차적으로 등록
    queueService.lineUp(USER_ID_1).block();
    queueService.lineUp(USER_ID_2).block();
    queueService.lineUp(USER_ID_3).block();

    // then: USER_ID_2는 대기열의 맨 앞에 있고, 전체 대기 인원은 2명
    StepVerifier.create(queueService.getStatus(USER_ID_2))
        .assertNext(status -> {
          assertThat(status.userQueuePosition()).isEqualTo(1L);
          assertThat(status.totalQueueSize()).isEqualTo(2L);
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("이미 입장 허용된 사용자가 다시 대기열에 등록하면 '이미 입장 가능' 메시지를 받는다")
  void lineUp_AlreadyAllowedUser_ReturnsAllowedMessage() {
    // given: 사용자1이 이미 입장 허용됨
    queueService.lineUp(USER_ID_1).block();

    // when: 같은 사용자가 다시 등록 시도
    StepVerifier.create(queueService.lineUp(USER_ID_1))
        .assertNext(message -> assertThat(message).isEqualTo("이미 입장 가능한 상태입니다."))
        .verifyComplete();
  }

  @Test
  @DisplayName("이미 대기 중인 사용자가 다시 대기열에 등록하려고 하면 대기열 뒤로 밀린다.")
  void lineUp_AlreadyWaitingUser_GoesBackToTheEndOfTheQueue() {
    // given: 사용자1 = 이미 입장 허용됨, 사용자 2,3 = 대기 중
    queueService.lineUp(USER_ID_1).block();
    queueService.lineUp(USER_ID_2).block();
    queueService.lineUp(USER_ID_3).block();

    // when: 사용자2가 다시 대기열에 등록 시도
    queueService.lineUp(USER_ID_2).block();

    // then: 사용자2는 대기번호=2, 뒤에 기다리는 인원 수=0
    StepVerifier.create(queueService.getStatus(USER_ID_2))
        .assertNext(res -> {
          assertThat(res.userQueuePosition()).isEqualTo(2L);
          assertThat(res.usersBehind()).isEqualTo(0L);
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("대기열에 없는 사용자의 상태 조회 시 예외가 발생한다")
  void getStatus_UserNotInQueue_ThrowsException() {
    // when & then
    StepVerifier.create(queueService.getStatus("non-existent-user"))
        .expectError(QueueException.class)
        .verify();
  }

  @Test
  @DisplayName("입장 허용 토큰을 제거하면 다음 사용자가 자동으로 입장 허용된다")
  void removeAllowedToken_AllowsNextUser() throws InterruptedException{
    // given: 여러 사용자가 대기 중
    queueService.lineUp(USER_ID_1).block();
    queueService.lineUp(USER_ID_2).block();
    queueService.lineUp(USER_ID_3).block();

    // when: 첫 번째 사용자의 토큰 제거
    queueService.removeAllowedToken(USER_ID_1).block();

    // then: 다음 사용자가 입장 가능해짐
    // 약간의 지연 후 확인 (비동기 처리 고려)
    try {
      Thread.sleep(100);
      StepVerifier.create(queueService.canEnter(USER_ID_2))
          .assertNext(canEnter -> assertThat(canEnter).isTrue())
          .verifyComplete();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  @DisplayName("만료된 토큰을 정리하면 해당 사용자의 입장 권한이 제거된다")
  void cleanupExpiredTokens_RemovesExpiredTokens() {
    // given: 사용자가 입장 허용됨
    queueService.lineUp(USER_ID_1).block();

    // 타임스탬프를 과거로 설정하기 위해 직접 Redis 조작
    String token = getTokenForUser(USER_ID_1);
    redis.opsForHash().put("allowedIn:users", token, "0").block();

    // when: 만료된 토큰 정리
    StepVerifier.create(queueService.cleanupExpiredTokens())
        .verifyComplete();

    // then: 더 이상 입장 불가
    StepVerifier.create(queueService.canEnter(USER_ID_1))
        .expectNext(false)
        .verifyComplete();
  }

  @Test
  @DisplayName("스케줄러가 주기적으로 만료된 토큰을 정리한다")
  void schedulerCleansExpiredTokens() throws InterruptedException {
    // given:
    // 입장 허용 시간 = 15초, 스케줄러 실행 주기 = 10초
    // 사용자 입장 허용 후 토큰을 과거 시간으로 설정
    queueService.lineUp(USER_ID_1).block();

    String token = getTokenForUser(USER_ID_1);
    redis.opsForHash().put("allowedIn:users", token, "0").block();

    // when: 스케줄러가 실행될 때까지 대기 (10초 + 여유시간)
    Thread.sleep(12000);

    // then: 토큰이 정리되어 입장 불가
    StepVerifier.create(queueService.canEnter(USER_ID_1))
        .expectNext(false)
        .verifyComplete();
  }

  private String getTokenForUser(String userId) {
    return HmacUtil.hmacSha26("test-secret-key-for-hmac", userId);
  }
}