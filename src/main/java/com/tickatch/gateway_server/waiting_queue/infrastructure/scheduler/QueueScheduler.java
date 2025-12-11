package com.tickatch.gateway_server.waiting_queue.infrastructure.scheduler;

import com.tickatch.gateway_server.waiting_queue.application.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

  private final WaitingQueueService queueService;

  @Scheduled(fixedRate = 10000, initialDelay = 10000)
  public void processNextEntry() {
    queueService.cleanupExpiredTokens()
        .doOnSuccess(v -> log.info("만료 토큰 정리 완료"))
        .onErrorResume(error -> {
          log.error("만료 토큰 정리 중 오류 발생", error);
          return Mono.empty();  // 에러를 삼켜서 다음 스케줄은 정상 실행되도록
        })
        .subscribe();
  }
}
