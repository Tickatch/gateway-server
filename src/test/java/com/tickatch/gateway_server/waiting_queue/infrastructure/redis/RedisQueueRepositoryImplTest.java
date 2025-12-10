package com.tickatch.gateway_server.waiting_queue.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.tickatch.gateway_server.global.util.HmacUtil;
import com.tickatch.gateway_server.waiting_queue.application.exception.QueueException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.test.StepVerifier;

@SpringBootTest
class RedisQueueRepositoryImplTest {

  @Autowired
  RedisQueueRepositoryImpl redisQueueRepositoryImpl;

  @Autowired
  private ReactiveRedisTemplate<String, String> redisTemplate;

  private static final String TEST_USER_ID_1 = "fe4142de-3633-4669-9655-c1ec8f88ae4c";
  private static final String TEST_USER_ID_2 = "23e19167-3a4f-477c-932d-ae16164577a6";
  private static final String TEST_USER_ID_3 = "a5d9719e-9921-4f1f-b67d-2e052d14b385";

  private static final String TEST_SECRET = "test-secret-key-for-hmac";
  private static final Long TIMESTAMP = 1234567890000L;

  private static final String WAITING_QUEUE_KEY = "queue:wait";
  private static final String ALLOWED_IN_LIST_KEY = "allowedIn:token:";

  @BeforeEach
  void setUp() {
    redisTemplate.getConnectionFactory()
        .getReactiveConnection()
        .serverCommands()
        .flushAll() // Redis의 모든 데이터베이스의 모든 키를 삭제
        .block();   // Reactive API이므로 완료될 때까지 블록킹/기다려야 함
  }

  @Test
  @DisplayName("사용자가 대기열에 진입하면 순번이 부여된다.")
  void lineUp_should_add_user_to_queue_with_sequence() {
    // given
    String token = HmacUtil.hmacSha26(TEST_SECRET, TEST_USER_ID_1 + ":" + TIMESTAMP);

    // when & then
    StepVerifier.create(redisQueueRepositoryImpl.lineUp(token)).verifyComplete();
    StepVerifier.create(redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, token))
        .assertNext(pos -> assertThat(pos).isEqualTo(0))
        .verifyComplete();
  }

  @Test
  @DisplayName("여러 사용자가 순차적으로 대기열에 진입하면 순서대로 순번이 부여된다.")
  void lineUp_multiple_users_should_assign_in_sequential_order() {
    // given
    String token1 = HmacUtil.hmacSha26(TEST_SECRET, TEST_USER_ID_1 + ":" + TIMESTAMP);
    String token2 = HmacUtil.hmacSha26(TEST_SECRET, TEST_USER_ID_2 + ":" + TIMESTAMP);
    String token3 = HmacUtil.hmacSha26(TEST_SECRET, TEST_USER_ID_3 + ":" + TIMESTAMP);

    // when
    // 실행이 하나씩 끝날 때까지 블록킹
    redisQueueRepositoryImpl.lineUp(token1).block();
    redisQueueRepositoryImpl.lineUp(token2).block();
    redisQueueRepositoryImpl.lineUp(token3).block();

    // then
    StepVerifier.create(redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, token1))
        .assertNext(pos -> assertThat(pos).isEqualTo(0L))
        .verifyComplete();
    StepVerifier.create(redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, token2))
        .assertNext(pos -> assertThat(pos).isEqualTo(1L))
        .verifyComplete();
    StepVerifier.create(redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, token3))
        .assertNext(pos -> assertThat(pos).isEqualTo(2L))
        .verifyComplete();
  }

  @Test
  @DisplayName("대기열에 있는 사용자는 자신의 현재 대기 상태(대기열 크기, 대기번호, 뒤에 있는 대기자 수)를 조회할 수 있다")
  void getCurrentStatus_should_return_queue_status() {
    // given
    String token1 = HmacUtil.hmacSha26(TEST_SECRET, TEST_USER_ID_1 + ":" + TIMESTAMP);
    String token2 = HmacUtil.hmacSha26(TEST_SECRET, TEST_USER_ID_2 + ":" + TIMESTAMP);
    String token3 = HmacUtil.hmacSha26(TEST_SECRET, TEST_USER_ID_3 + ":" + TIMESTAMP);

    // 실행이 하나씩 끝날 때까지 블록킹
    redisQueueRepositoryImpl.lineUp(token1).block();
    redisQueueRepositoryImpl.lineUp(token2).block();
    redisQueueRepositoryImpl.lineUp(token3).block();

    // when & then
    // 2번째 사용자 기준 조회 시
    // {
    //   대기열 크기 = 3,
    //   대기번호 = 2,
    //   뒤에 있는 대기자 수 = 1
    // }


    StepVerifier.create(redisQueueRepositoryImpl.getCurrentStatus(token2))
        .assertNext(status -> {
          assertThat(status.totalQueueSize()).isEqualTo(3L);
          assertThat(status.userQueuePosition()).isEqualTo(2L);
          assertThat(status.usersBehind()).isEqualTo(1L);
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("대기열에 없는 사용자의 상태를 조회하면 에러가 발생한다")
  void getCurrentStatus_user_not_in_queue_should_throw_error() {
    // given
    String token1 = HmacUtil.hmacSha26(TEST_SECRET, TEST_USER_ID_1 + ":" + TIMESTAMP);

    redisQueueRepositoryImpl.lineUp(token1).block();

    // when & then
    StepVerifier.create(redisQueueRepositoryImpl.getCurrentStatus("random-token"))
        .expectError(QueueException.class)
        .verify();
  }

  @Test
  @DisplayName("다음 사용자를 입장 허용하면 대기열 맨 앞 사용자가 제거되고 입장 허용 키가 생성된다")
  void allowNextUser_should_remove_from_queue_and_create_allowedIn_key() {
    // given
    String token1 = HmacUtil.hmacSha26(TEST_SECRET, TEST_USER_ID_1 + ":" + TIMESTAMP);
    String token2 = HmacUtil.hmacSha26(TEST_SECRET, TEST_USER_ID_2 + ":" + TIMESTAMP);
    String token3 = HmacUtil.hmacSha26(TEST_SECRET, TEST_USER_ID_3 + ":" + TIMESTAMP);

    redisQueueRepositoryImpl.lineUp(token1).block();
    redisQueueRepositoryImpl.lineUp(token2).block();
    redisQueueRepositoryImpl.lineUp(token3).block();

    // when
    redisQueueRepositoryImpl.allowNextUser().block();

    // then
    // 대기열에서 사용자가 제거되었음을 검증
    StepVerifier.create(redisTemplate.opsForZSet().score(WAITING_QUEUE_KEY, token1))
        .verifyComplete();

    // 대기열 크기 확인
    StepVerifier.create(redisTemplate.opsForZSet().size(WAITING_QUEUE_KEY))
        .assertNext(size -> assertThat(size).isEqualTo(2L))
        .verifyComplete();

    // 입장 허용 키가 생성되었음을 검증
    StepVerifier.create(redisTemplate.opsForValue().get(ALLOWED_IN_LIST_KEY + token1))
        .assertNext(value -> assertThat(value).isEqualTo("OK"))
        .verifyComplete();
  }

  @Test
  @DisplayName("allowNextUser()는 TTL 4분의 입장 허용 키를 생성한다")
  void allowNextUser_sets_ttl_4_minutes() {
    // given
    String token1 = HmacUtil.hmacSha26(TEST_SECRET, TEST_USER_ID_1 + ":" + TIMESTAMP);
    redisQueueRepositoryImpl.lineUp(token1).block();


    // when
    redisQueueRepositoryImpl.allowNextUser().block();

    // then
    // 대기열에서 사용자가 제거되었음을 검증
    StepVerifier.create(redisTemplate.getExpire(ALLOWED_IN_LIST_KEY + token1))
        .assertNext(ttl -> assertThat(ttl.getSeconds() <= 240 && ttl.getSeconds() >= 235))
        .verifyComplete();
  }
}