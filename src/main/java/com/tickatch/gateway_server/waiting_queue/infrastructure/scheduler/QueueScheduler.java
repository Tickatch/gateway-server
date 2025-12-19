package com.tickatch.gateway_server.waiting_queue.infrastructure.scheduler;

import com.tickatch.gateway_server.waiting_queue.application.QueueStatusNotifier;
import com.tickatch.gateway_server.waiting_queue.application.WaitingQueueService;
import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusChangeEvent;
import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

  private static final String WAITING_QUEUE_KEY = "queue:wait";

  private final WaitingQueueService queueService;
  private final QueueStatusNotifier notifier;
  private final ReactiveRedisTemplate<String, String> redis;

  @Scheduled(fixedRate = 300000, initialDelay = 300000)
  @SchedulerLock(
      name = "allowedInTokenCleanup",
      lockAtMostFor = "4m",
      lockAtLeastFor = "4m"
  )
  public void processNextEntry() {
    queueService.cleanupExpiredTokens()
        .doOnSuccess(v -> log.info("만료 토큰 정리 완료"))
        .onErrorResume(error -> {
          log.error("만료 토큰 정리 중 오류 발생", error);
          return Mono.empty();  // 에러를 삼켜서 다음 스케줄은 정상 실행되도록
        })
        .subscribe();
  }

  /**
   * 대기열 순번 변경 알림 (10초마다)
   *
   * SSE 구독 중인 사용자에게만 현재 대기열 상태를 전송
   * 구독자가 없으면 스킵하여 불필요한 Redis 조회 방지
   */
  @Scheduled(fixedRate = 10000, initialDelay = 10000)
  @SchedulerLock(
      name = "notifyQueueStatusUpdates",
      lockAtMostFor = "9s",
      lockAtLeastFor = "8s"
  )
  public void notifyQueueStatusUpdates() {
    if (notifier.getActiveSubscribers() == 0) {
      return;
    }

    redis.opsForZSet()
        .size(WAITING_QUEUE_KEY)
        .flatMapMany(queueSize -> {
          if (queueSize == 0) {
            return Flux.empty();
          }

          return redis.opsForZSet().range(WAITING_QUEUE_KEY, Range.unbounded())
              .index()
              .doOnNext(tuple -> {
                long position = tuple.getT1() + 1;
                String userId = tuple.getT2();

                long usersBehind = queueSize - position;

                QueueStatusChangeEvent event =  new QueueStatusChangeEvent(
                    new QueueStatusResponse(queueSize, position, usersBehind)
                );

                notifier.notifyStatusChange(userId, event);
              });
        }).subscribe();
  }
}
