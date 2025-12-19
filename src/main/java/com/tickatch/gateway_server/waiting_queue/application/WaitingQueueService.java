package com.tickatch.gateway_server.waiting_queue.application;

import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
import com.tickatch.gateway_server.waiting_queue.application.port.QueueRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueService {

  private final QueueRepository queueRepository;
  private final QueueStatusNotifier notifier;

  public Mono<String> lineUp(String userId) {
    return queueRepository.lineUp(userId);
  }

  public Mono<Boolean> canEnter(String userId) {
    return queueRepository.isAlreadyAllowedIn(userId);
  }

  public Mono<QueueStatusResponse> getStatus(String userId) {
    return queueRepository.getCurrentStatus(userId);
  }

  public Mono<Void> refreshAllowedInTimeStamp(String userId) {
    return queueRepository.refreshAllowedInTimestamp(userId);
  }

  public Mono<Boolean> removeAllowedToken(String userId) {
    return queueRepository.removeAllowedToken(userId)
        .flatMap(result -> {
          log.info("result = {}", result);
          if (!result.removed()) {
            return Mono.just(false);
          }

          // 다음 대기자가 입장되어 SSE 알림 전송
          String nextUserId = result.nextUserId();
          if (nextUserId != null) {
            notifier.notifyAllowedIn(nextUserId);
          }

          return Mono.just(true);
        });
  }

  public Mono<Void> cleanupExpiredTokens() {
    return queueRepository.cleanupExpiredTokens()
        .flatMap(result -> {
          List<String> userIds = result.userIds();
          if (!userIds.isEmpty()) {
            userIds.forEach(notifier::notifyAllowedIn);
          }

          return Mono.empty();
        });
  }

  public Mono<Boolean> removeWaitingToken(String userId) {
    return queueRepository.removeWaitingToken(userId);
  }
}
